
package rice.pastry.client;

import rice.environment.logging.Logger;
import rice.p2p.commonapi.appsocket.*;
import rice.p2p.commonapi.rawserialization.MessageDeserializer;
import rice.pastry.*;
import rice.pastry.messaging.*;
import rice.pastry.standard.*;
import rice.pastry.routing.*;
import rice.pastry.leafset.*;

import java.io.IOException;
import java.util.*;

/**
 * A PastryAppl is an abstract class that every Pastry application
 * extends.  This is the external Pastry API.
 *
 * @version $Id$
 *
 * @author Peter Druschel
 */
public abstract class PastryAppl implements Observer
{
  protected MessageDeserializer deserializer;
  
  // private block
  protected String instance;

  protected PastryNode thePastryNode;

  protected int address;

  protected Logger logger;
  
  /**
   * Buffered while node is not ready to prevent inconsistent routing.
   */
  LinkedList undeliveredMessages = new LinkedList();
  
  private class LeafSetObserver implements NodeSetListener {
    public void nodeSetUpdate(NodeSetEventSource nodeSetEventSource, NodeHandle handle, boolean added) {
      leafSetChange(handle, added);
    }
  }

  private class RouteSetObserver implements NodeSetListener {
    public void nodeSetUpdate(NodeSetEventSource nodeSetEventSource, NodeHandle handle, boolean added) {
      routeSetChange(handle, added);
    }
  }

  // constructor

  /**
   * Constructor. 
   *
   * @param pn the pastry node that client will attach to.
   */
  public PastryAppl(PastryNode pn) {
    this(pn, null);
  }
  
  /**
   * Constructor.  This constructor will perform the same tasks as the
   * above constructor, but will also create a Pastry address for this
   * application, which is dependent upon the given instance name and
   * the class name.
   *
   * @param pn the pastry node that client will attach to.
   * @param instance The instance name of this appl.
   */
  public PastryAppl(PastryNode pn, String instance) {
    this(pn, instance, 0, null);
    register();
  }
  
  public PastryAppl(PastryNode pn, String instance, int address, MessageDeserializer md) {
    this.address = address;
    if (instance != null) {
      this.instance = instance;
      if (address == 0)
        this.address = StandardAddress.getAddress(this.getClass(), instance, pn.getEnvironment());
    }
    
    thePastryNode = pn;
    logger = pn.getEnvironment().getLogManager().getLogger(getClass(), instance);
    deserializer = md;
    if (deserializer == null)
      deserializer = new JavaSerializedDeserializer(pn);
  }
    
  /**
   * Constructor.  This constructor will perform the same tasks as the
   * above constructor, but will also create a Pastry address for this
   * application, using the specified port.
   * 
   * Need to call register on this.
   *
   * @param pn the pastry node that client will attach to.
   * @param instance The instance name of this appl.
   */
  public PastryAppl(PastryNode pn, int port) {
    this(pn, null, port, null);
  }
  
  public void register() {
    thePastryNode.registerReceiver(getAddress(), this);

    thePastryNode.addLeafSetListener(new LeafSetObserver());
    thePastryNode.addRouteSetListener(new RouteSetObserver());

    thePastryNode.registerApp(this); // just adds it to a list    
    
    thePastryNode.addObserver(this);
  }
  
  /**
   * Returns the address of this application.
   *
   * @return the address.
   */
  public int getAddress() {
    return address;
  }

  // internal methods

  public void receiveMessageInternal(RawMessageDelivery msg) {
    Message m;
    try {
      m = msg.deserialize(deserializer);
    } catch (IOException ioe) {
      if (logger.level <= Logger.SEVERE) logger.logException("Error deserializing "+msg+" in "+this+".  Message will be dropped.", ioe);
      return;
    } catch (RuntimeException re) {
      if (logger.level <= Logger.SEVERE) logger.logException("Error deserializing "+msg+" in "+this+".  Message will be dropped.", re);
      throw re;
    }
    receiveMessage(m);
  }
  
  protected void setDeserializer(MessageDeserializer deserializer) {
    this.deserializer = deserializer; 
  }
  
  /**
   * Called by pastry to deliver a message to this client.
   *
   * @param msg the message that is arriving.
   */
  public void receiveMessage(Message msg) {
    // NOTE: the idea is to synchronize on undeliveredMessages while making the check as to whether or not to add to the queue
    // the other part of the synchronization is just below, in update()
    // we don't want to hold the lock to undeliveredMessages when calling receiveMessage()
    synchronized(undeliveredMessages) {
      if (deliverWhenNotReady() || thePastryNode.isReady()) {
        // continue to receiveMessage()
      } else {
        // enable this if you want to forward RouteMessages when not ready, without calling the "forward()" method on the PastryAppl that sent the message
//      if (msg instanceof RouteMessage) {
//        RouteMessage rm = (RouteMessage)msg;
//        rm.routeMessage(this.localNode.getLocalHandle());
//        return;
//      }
//        undeliveredMessages.add(msg);
        return;
      }
    }    
    
    if (logger.level <= Logger.FINER) logger.log(
        "[" + thePastryNode + "] recv " + msg);
    if (msg instanceof RouteMessage) {
      RouteMessage rm = (RouteMessage) msg;

      try {
        if (enrouteMessage(rm.unwrap(deserializer), rm.getTarget(), rm.nextHop, rm.getOptions()))
          rm.routeMessage(thePastryNode.getLocalHandle());
      } catch (IOException ioe) {
        throw new RuntimeException("Error deserializing message "+rm,ioe); 
      }
    }
    else messageForAppl(msg);
  }

  public void update(Observable arg0, Object arg1) {
    if (arg0 == thePastryNode) {
      Boolean b = (Boolean)arg1;
      if (b.booleanValue()) {
        Collection copy;
        synchronized(undeliveredMessages) {
          copy = new ArrayList(undeliveredMessages); 
          undeliveredMessages.clear();
        }
        Iterator i = copy.iterator();
        while(i.hasNext()) {
          Message m = (Message)i.next(); 
          receiveMessage(m);
        }
      }
    }
  }
  
  // useful API methods

  /**
   * Gets the node id associated with this client.
   *
   * @return the node id.
   */
  public final Id getNodeId() { return thePastryNode.getNodeId(); }

  /**
   * Gets the handle of the Pastry node associated with this client
   *
   * @return the node handle
   */
  public NodeHandle getNodeHandle() {
    return thePastryNode.getLocalHandle();
  }

  /**
   * Sends a message to the Pastry node identified by dest. If that
   * node has failed or no point-to-point connection can be
   * established to the node from the local node in the Internet,
   * the operation fails. Note that in this case, it may still be
   * possible to send the message to that node using routeMsg.
   *
   * @param dest the destination node
   * @param msg the message to deliver.
   * @param cred credentials that verify the authenticity of the message.
   * @param opt send options that describe how the message is to be routed.  
   */
  public boolean routeMsgDirect(NodeHandle dest, Message msg, SendOptions opt) {
    if (logger.level <= Logger.FINER) logger.log(
        "[" + thePastryNode + "] routemsgdirect " + msg + " to " + dest);
    if (!dest.isAlive()) return false;
    //RouteMessage rm = new RouteMessage(dest, msg, cred, opt, getAddress());
    //thePastryNode.receiveMessage(rm);

    // XXX Does routeMsgDirect need credentials?
    // Arguably, leafset messages don't need credentials because
    // individual nodeids may be signed. (not entirely true..)
    // But routeMsgDirect messages *do* need credentials. So do we
    // go back to using options to differentiate from routeMsg?

    thePastryNode.send(dest,msg);
    return dest.isAlive();
  }

  /**
   * Routes a message to the live node D with nodeId numerically
   * closest to key (at the time of delivery).  The message is
   * delivered to the application with address addr at D, and at
   * each Pastry node encountered along the route to D.
   *
   * @param key the key
   * @param msg the message to deliver.
   * @param cred credentials that verify the authenticity of the message.
   * @param opt send options that describe how the message is to be routed.
   */
  public void routeMsg(Id key, Message msg, SendOptions opt) {
    if (logger.level <= Logger.FINER) logger.log(
        "[" + thePastryNode + "] routemsg " + msg + " to " + key);
    RouteMessage rm = new RouteMessage(key, msg, opt);
    thePastryNode.receiveMessage(rm);
  }

  /**
   * Called by a layered Pastry application to obtain a copy of the leaf
   * set. The leaf set contains the nodeId to IP address binding of the
   * l/2 nodes with numerically closest counterclockwise and the l/2 nodes with
   * numerically closest clockwise nodeIds, relatively to the local node's
   * id.
   *
   * @return the local node's leaf set
   */
  public LeafSet getLeafSet() {
    return thePastryNode.getLeafSet().copy();
  }

  /**
   * Called by a layered Pastry application to obtain a copy of the
   * routing table. The routing table contains the nodeId to IP
   * address bindings of R nodes that share the local node's id in
   * the first n digits, and differ in the n+1th digit, for 0 <= n
   * <= ceiling(log_2^b N), where N is the total number of currently
   * live nodes in the Pastry network. The routing table may be
   * incomplete, may contain nodes that cannot be reached from the
   * local node or have failed, and the table may change at any
   * time.
   */
  public RoutingTable getRoutingTable() {
    return thePastryNode.getRoutingTable();
  }

  /**
   * Called by the layered Pastry application to check if the local
   * pastry node is the one that is currently closest to the object key id.
   *
   * @param key
   * the object key id
   *
   * @return true if the local node is currently the closest to the key.
   */
  public boolean isClosest(Id key) {
    return thePastryNode.isClosest(key);
  }

  // abstract methods, to be overridden by the derived application object

  /**
   * Called by pastry when a message arrives for this application.
   *
   * @param msg the message that is arriving.
   */
  public abstract void messageForAppl(Message msg);

  /**
   * Called by pastry when a message is enroute and is passing through this node.  If this
   * method is not overridden, the default behaviour is to let the message pass through.
   *
   * @param msg the message that is passing through.
   * @param key the key
   * @param nextHop the default next hop for the message.
   * @param opt the send options the message was sent with.
   *
   * @return true if the message should be routed, false if the message should be cancelled.
   */
  public boolean enrouteMessage(Message msg, Id key, NodeHandle nextHop, SendOptions opt) {
    return true;
  }

  /**
   * Called by pastry when the leaf set changes.
   *
   * @param nh the handle of the node that was added or removed.
   * @param wasAdded true if the node was added, false if the node was removed.
   */
  public void leafSetChange(NodeHandle nh, boolean wasAdded) {}

  /**
   * Called by pastry when the route set changes.
   *
   * @param nh the handle of the node that was added or removed.
   * @param wasAdded true if the node was added, false if the node was removed.
   */
  public void routeSetChange(NodeHandle nh, boolean wasAdded) {}

  /**
   * Invoked when the Pastry node has joined the overlay network and
   * is ready to send and receive messages
   * 
   * As of FreePastry 1.4.1, replaced by PastryNode Observer pattern.
   */
  public void notifyReady() {}

  /**
   * Instructs the MessageDispatch how to behave when the PastryNode is not ready.
   * 
   * An application can override this method to return true if it wishes to receive
   * messages before Pastry is ready().
   * 
   * Most applications should leave this as false, so that their application does 
   * not have inconsistent routing.  However Pastry's protocols (such as the join protocol)
   * need to receive messages before pastry is ready().  This is because they are attempting
   * to make pastry ready().
   * 
   * @return false unless the node is a service
   */
  public boolean deliverWhenNotReady() {
    return false;
  }  
  
  /**
   * Called when PastryNode is destroyed.  Can be overloaded by applications.
   */
  public void destroy() {}
  

  // *******************  Applicatoin Socket Interface *****************
  /**
   * Called to open an ApplicationLevelSocket
   */
  public void connect(NodeHandle handle, AppSocketReceiver receiver, int timeout) {
    thePastryNode.connect(handle, receiver, this, timeout);    
  }  

  /**
   * Sets an AppSocketReceiver to be called when the next socket arrives.
   * @param receiver
   */
  public void accept(AppSocketReceiver receiver) {        
    this.receiver = receiver;
  }  
  
  /**
   * holds the receiverSocket
   */
  protected AppSocketReceiver receiver;
  
  /**
   * Calls receiver.receiveSocket(), then sets receiver to null.
   * 
   * It sets it to null to allow the application to provide SocketInitiationBackpressure
   * 
   * @param socket the new socket from the network
   * @return false if receiver was null, true receiveSocket() was called
   */
  public boolean receiveSocket(AppSocket socket) {
    AppSocketReceiver theReceiver = receiver;
    receiver = null;
    if (theReceiver == null) {
      return false;
    } else {
      theReceiver.receiveSocket(socket);
      return true;
    }
  }
  
}


