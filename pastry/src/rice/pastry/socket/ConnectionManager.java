/*
 * Created on Mar 31, 2004
 *
 */
package rice.pastry.socket;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.TimerTask;

import rice.pastry.NodeHandle;
import rice.pastry.leafset.BroadcastLeafSet;
import rice.pastry.messaging.Message;
import rice.pastry.routing.RouteMessage;
import rice.pastry.socket.messaging.AckMessage;
import rice.pastry.socket.messaging.SocketControlMessage;
import rice.pastry.socket.messaging.SocketTransportMessage;

/**
 * This class manages 2 SocketManagers for a particular address.  
 * <HR><HR>
 * 1. A control/routing connection which is acked, and has a maximum size message.
 * 2. A data connection which can have messages of any size and is not acked.
 * <HR>
 * In addition, this class manages the liveness for a particular connection.  
 * It uses a PingManager and the AckTimeoutEvent to do this.
 * 
 * @author Jeff Hoye
 */
public class ConnectionManager {

  SocketManager controlSocketManager;
  SocketManager dataSocketManager;
  PingManager pingManager;
  SocketCollectionManager scm;
  InetSocketAddress address;


  
  // *************** Control/Acking Fields *********************
  /**
   *  the maximum length of the queue
   */
  public static int MAXIMUM_QUEUE_LENGTH = 12800;
  
  /**
   * The maximum number of in flight messages (not including acks).
   */
  public static final int MAX_PENDING_ACKS = 1;

  /**
   * The acks we are waiting for
   * Sequence Number(Integer) -> AckTimeoutEvent
   */
  private Hashtable pendingAcks;

  /**
   * The current sequence number for the control socket.
   */
  private int currentAckNumber = 0;
  
  /**
   * Internal list of objects waiting to be written over control channel.
   */
  private LinkedList queue;

  /**
   * The time before we call checkDead after we schedule the message for writing.
   */
  long ACK_DELAY = 1500;

  /**
   * The types of traffic that can be sent
   */
  public static final int TYPE_CONTROL = 1;
  public static final int TYPE_DATA = 2;
  
  /**
   * The maximum message size for a routing entry
   */
  public static final int MAX_ROUTE_MESSAGE_SIZE = 64000;



  // *************** Liveness Fields *********************
  /**
   * NodeHandle.LIVENESS_ALIVE, NodeHandle.LIVENESS_SUSPECTED, NodeHandle.LIVENESS_FAULTY, NodeHandle.LIVENESS_UNKNOWN
   */
  int liveness;

  long PING_DELAY = 3000;
  int NUM_PING_TRIES = 3;


  /**
   * A lock around deadChecker.
   */
  Object livenessLock = new Object();
  
  /**
   * Non-Null if we are currently pinging the node.
   */
  DeadChecker deadChecker;

	/**
	 * Constructs a new ConnectionManager.  
   * This does NOT automatically open a connection for control or data traffic.
	 */
	public ConnectionManager(SocketCollectionManager scm, InetSocketAddress address) {
    this.scm = scm;   
    this.address = address;
    this.pingManager = scm.getPingManager();
    liveness = NodeHandle.LIVENESS_UNKNOWN; 
	}

  // ********************** Socket Lifecycle ***********************
  /**
   * Method which opens a socket to a given remote node handle, and updates the
   * bookkeeping to keep track of this socket
   *
   * @param address DESCRIBE THE PARAMETER
   */
  protected void openSocket(int type) {
    SocketManager sm = null;
    try {
      sm = new SocketManager(address, scm, this, type);
      checkDead();
      scm.getSocketPoolManager().socketOpened(sm);
    } catch (IOException e) {
      System.out.println("GOT ERROR " + e + " OPENING DATA SOCKET!");
      e.printStackTrace();
      if (sm != null) {
        sm.close();
      }
    }            

    if (type == TYPE_DATA) {
      dataSocketManager = sm;
    } else {
      controlSocketManager = sm;
      initializeControlSocketManager();
    }
  }

  protected void initializeControlSocketManager() {
    pendingAcks = new Hashtable();
    queue = new LinkedList();    
  }

  /**
   * called by SocketCollectionManager when a remote node opens a socket.
   * @param sm The new SocketManager opened by the remote node
   */
  public void acceptSocket(SocketManager sm) {
    if (sm.getType() == TYPE_CONTROL) {
      if (controlSocketManager == null) {
        initializeControlSocketManager();
        controlSocketManager = sm;
        sm.setConnectionManager(this);
        scm.getSocketPoolManager().socketOpened(sm);
      } else {
        controlSocketManager = handleSocketCollision(controlSocketManager, sm);
      }
    } else {
      if (dataSocketManager == null) {
        dataSocketManager = sm;
        sm.setConnectionManager(this);
        scm.getSocketPoolManager().socketOpened(sm);
      } else {
        dataSocketManager = handleSocketCollision(dataSocketManager, sm);
      }
    }
  }

  /**
   * Called when there are 2 connections of the same type opened to the same node.  It closes the 
   * one with the lower hashcode for the address/port
   * @param existing
   * @param newMgr
   * @return
   */
  protected SocketManager handleSocketCollision(SocketManager existing, SocketManager newMgr) {
    debug("ERROR: Request to record socket opening for already-open socket to " + address + " of type:"+existing.getType());
    String local = "" + scm.localAddress.getAddress().getHostAddress() + scm.localAddress.getPort();
    String remote = "" + address.getAddress().getHostAddress() + address.getPort();
  
    debug("RESOLVE: Comparing " + local + " and " + remote);
  
    if (remote.compareTo(local) < 0) {
      debug("RESOLVE: Cancelling existing data connection to " + address);  
      scm.getSocketPoolManager().socketClosed(existing);
      scm.getSocketPoolManager().socketOpened(newMgr);
      existing.close();
      initializeControlSocketManager();
      newMgr.setConnectionManager(this);
      return newMgr;
    } else {
      debug("RESOLVE: Cancelling new connection to " + address);
      return existing;
    }
  }
  
  /**
   * Called when a socket is closed to set the socketManger to null.
   * @param manager The SocketManager that was closed.
   */
  public void socketClosed(SocketManager manager) {
    System.out.println("CM.socketClosed("+manager.getType()+")");
    if (manager.getType() == TYPE_CONTROL) {
      if (manager == controlSocketManager) {
        controlSocketManager = null;
      }
    } else {
      if (manager == dataSocketManager) {
        dataSocketManager = null;
      }
    }
    scm.getSocketPoolManager().socketClosed(manager);
  }  


  // *************** Message Queueing *****************
  /**
   * Sends the message over the apropriate channel.  
   * <HR><HR>
   * If the tyep is TYPE_CONTROL then the message will be queued 
   * here.  Messages can be scheduled for writing when the number 
   * of messages in flight drop below MAX_PENDING_ACKS.
   * 
   * @param message The message to send.
   * @param type SocketCollectionManager.TYPE_CONTROL, SocketCollectionManager.TYPE_DATA
   */
  public void send(Message message) {
    int type = getMessageType(message);
    if (type == TYPE_CONTROL) {  
      if (controlSocketManager == null) {
        debug("No control connection open to " + address + " - opening one");
        openSocket(TYPE_CONTROL);        
      }

      if (controlSocketManager != null) {
          enqueue(message);
          moveMessagesToControlSM();
      } else {
        debug("ERROR: Could not connection to remote address " + address + " rerouting message " + message);
        scm.reroute(address, message);
      }
    } else { // type == TYPE_DATA
      if (dataSocketManager == null) {
        debug("No data connection open to " + address + " - opening one");
        openSocket(TYPE_DATA);        
      }

      if (dataSocketManager != null) {
        //debug("Found connection open to " + address + " - sending now");
        
        dataSocketManager.send(message);        
        scm.getSocketPoolManager().socketUpdated(dataSocketManager);
      } else {
        debug("ERROR: Could not connect to remote address " + address + " for data, dropping message " + message);
      }      
    }
  } // send()


  /**
   * This method determines which socket the message should be sent over.  
   * @param m the message
   * @return TYPE_CONTROL or TYPE_DATA
   */
  public int getMessageType(Message m) {
    if (m.hasPriority() || (m instanceof RouteMessage)) {
      return TYPE_CONTROL;
    }
    System.out.println("Sending "+m+" over data pipe.");
    return TYPE_DATA;
  }

// *********************** Control Traffic Handlers **********************
	/**
   * Enqueues the object for sending over the control/routing channel by priority.
   * 
   * @param o The object to be queued.
   * @return true if the queue was not full
   */
  public boolean enqueue(Message o) {
    if (!scm.isAlive(address)) {
      System.err.println(System.currentTimeMillis()+":"+scm.pastryNode.getNodeId()+"->"+ address + " remote node appears dead " + o + " will be dropped.");      
      Thread.dumpStack();
      return false;
    }
    synchronized (queue) {
      if (queue.size() < MAXIMUM_QUEUE_LENGTH) {
        addToQueue(o);
        return true;
      } else {
        System.err.println(System.currentTimeMillis()+":"+scm.pastryNode.getNodeId()+"->"+ address + " (CM): Maximum TCP queue length reached - message " + o + " will be dropped.");
        if (o instanceof Message) {
          //rerouteMessage((Message)o);
        }
//        Iterator i = queue.iterator();
//        while (i.hasNext()) {
//          System.out.println(i.next());
//        }        
        return false;
      }
    }
  }

  /**
   * Adds an entry into the control/routing queue, taking message 
   * prioritization into account.
   *
   * @param o The feature to be added to the ToQueue attribute
   */
  private void addToQueue(Message o) {
    boolean priority = o.hasPriority();

    if ((priority) && (queue.size() > 0)) {
      for (int i = 1; i < queue.size(); i++) {
        Object thisObj = ((MsgEntry)queue.get(i)).message;

        if ((thisObj instanceof Message) && (!((Message) thisObj).hasPriority())) {
          debug("Prioritizing socket message " + o + " over message " + thisObj);

          queue.add(i, new MsgEntry(o));
          return;
        }
      }
    }

    queue.addLast(new MsgEntry(o));
  }

  /**
   * Schedules up to MAX_PENDING_ACKS messages into the controlSocketManager from the queue.
   * This is called when we get an ack, or we schedule a new message.
   */
  private void moveMessagesToControlSM() {
    synchronized(queue) {
      while (!queue.isEmpty() && canWrite()) {
        Object message = ((MsgEntry)queue.removeFirst()).message;
        if (message instanceof SocketControlMessage) {
          controlSocketManager.send(message);            
        } else {
          try {
            SocketTransportMessage stm = new SocketTransportMessage((Message)message,currentAckNumber++);
            registerMessageForAck(stm); 
            controlSocketManager.send(stm);
          } catch (ClassCastException cce) {
            cce.printStackTrace();
            System.out.println(message.getClass().getName());
          }
        }

      }
    }
        
  }


  /**
   * This is the function that decides wether we can schedule more in
   * flight messages.  It currently only looks at the number of messages
   * in flight.  But may eventually take liveness into account.
   * 
   * @return wether we can schedule inflight messages.
   */
  public boolean canWrite() {
//    if (pendingAcks.size() > 3) {
//      System.out.println("CM.canWrite():"+pendingAcks.size());
//    }
    return pendingAcks.size() < MAX_PENDING_ACKS;
  }

  /**
   * Called by the SM when a message is read.
   * @param message The message.
   */
  public void receiveMessage(Message o) {
    if (o instanceof SocketTransportMessage) {
      SocketTransportMessage stm = (SocketTransportMessage)o;
      sendAck(stm);
      scm.pastryNode.receiveMessage(stm.msg);
      return;
    } else if (o instanceof AckMessage) {
      ackReceived((AckMessage)o);
      return;
    }
    scm.pastryNode.receiveMessage(o);
  }


  /**
   * Called by controlSocketManager whenever a SocketTransportMessage is received.
   * This sends an ack.  It doesn't enque the ack locally, it schedules it into the 
   * controlSocketManger immeadiately.
   * 
   * @param smsg the SocketTransportMessage that was received.
   */
  protected void sendAck(SocketTransportMessage smsg) {
//    System.out.println(this+".sendAck("+smsg+")");
    controlSocketManager.send(new AckMessage(smsg.seqNumber,smsg));      
  }

  /**
   * Called whenever we schedule a new message over the controlSocketManager.
   * @param msg
   */
  protected void registerMessageForAck(SocketTransportMessage msg) {
    synchronized(pendingAcks) {
      AckTimeoutEvent ate = new AckTimeoutEvent(msg);
      pendingAcks.put(new Integer(ate.ackNum),ate);
      scm.pastryNode.scheduleTask(ate, ACK_DELAY);
//      System.out.println(this+".registerMsg4Ack("+ate+"):type="+type);
    }
  }

  /**
   * Called by controlSocketManager when an Ack is read.  This will schedule more messages to be 
   * sent if possible.
   * @param am the AckMessage that came off the wire.
   */
  public void ackReceived(AckMessage am) {
    int i = am.seqNumber;
    //System.out.println(this+" SM.ackReceived("+i+") on type :"+type);
    markAlive(); //setLiveness(NodeHandle.LIVENESS_ALIVE);
    AckTimeoutEvent ate;
    synchronized(pendingAcks) {
      ate = (AckTimeoutEvent)pendingAcks.remove(new Integer(i));
      
      if (ate != null) {
//        System.out.println(this+" SM.ackReceived("+i+") on type :"+type+"elapsed Time:"+ate.elapsedTime());
        ate.cancel();              
      } else {
        System.out.println(this+" SM.ackReceived("+i+"):Ack received late");
      }
    }

    moveMessagesToControlSM();
/*
    long elapsedTime = -1;
    if (ate!=null) {
      elapsedTime = ate.elapsedTime();
    }
//    System.out.println("CM.ackReceived("+i+","+elapsedTime+")");
 
 */
  }

  /**
   * Called by AckTimeoutEvent when a message took to long to respond.
   * @param ate The timeout event.
   */
  public void ackNotReceived(AckTimeoutEvent ate) {
    //System.out.println(this+" SM.ackNotReceived("+ate+")");
    synchronized(pendingAcks) {
      AckTimeoutEvent nate = (AckTimeoutEvent)pendingAcks.get(new Integer(ate.ackNum));
      if (nate == null) {
        // already received ack
        return;
      }
    }
    checkDead();
  }

  // ******************* Liveness methods *****************
  /**
   * Gets the liveness status of this node.
   * 
   * @return NodeHandle.LIVENESS_ALIVE, NodeHandle.LIVENESS_SUSPECTED, NodeHandle.LIVENESS_FAULTY, NodeHandle.LIVENESS_UNKNOWN
   */
  public int getLiveness() {
    //System.out.println(this+".getLiveness()"+liveness);
    return liveness;
  }

  /**
   * Sets the liveness status of this node.
   * 
   * @param newStatus NodeHandle.LIVENESS_ALIVE, NodeHandle.LIVENESS_SUSPECTED, NodeHandle.LIVENESS_FAULTY, NodeHandle.LIVENESS_UNKNOWN
   */
  private void setLiveness(int newLiveness) {
    if ((liveness == NodeHandle.LIVENESS_FAULTY) &&
        (newLiveness == NodeHandle.LIVENESS_SUSPECTED)) {
          Thread.dumpStack();
      System.out.println("Attempting to downgrade faultyness without setting to alive command rejected");    
      return;  
    } 
    liveness = newLiveness;      
  }


  /**
   * Starts pinging the remote node if apropriate.
   *
   */
  public void checkDead() {
    if (address == null) {
      //System.out.println("checkDead called with null address");
      return;
    }    
    
    synchronized(livenessLock) {
      if (deadChecker == null) { 
        if (getLiveness() == NodeHandle.LIVENESS_FAULTY) {
          Thread.dumpStack();        
        }
        deadChecker = new DeadChecker(address, NUM_PING_TRIES, pingManager);
        scm.scheduleTask(deadChecker, PING_DELAY, PING_DELAY);
        pingManager.forcePing(address, deadChecker);
      }      
    }
  }
  
  void markSuspected() {
    setLiveness(NodeHandle.LIVENESS_SUSPECTED);
    rerouteQueueAndPendingAcks(); // will only reroute route messages, and not delete anything else
  }
  
  public void markDead() {
    setLiveness(NodeHandle.LIVENESS_FAULTY);
    scm.markDead(address);
    if (deadChecker != null) {
      deadChecker.cancel();
      deadChecker.tries = deadChecker.NUM_TRIES_DECLARE_FAULTY;
    }
    rerouteQueueAndPendingAcks(); // will reroute all route messages and throw out everything else
  }
  
  void markAlive() {
    if (getLiveness() == NodeHandle.LIVENESS_ALIVE) return;
    synchronized(livenessLock) {
      // true if we are switching from faulty to alive
      boolean needToNotifySCM = false;
      if (getLiveness() == NodeHandle.LIVENESS_FAULTY) {
        needToNotifySCM = true;
      }
      if (deadChecker != null) {
        deadChecker.cancel();
        setLiveness(NodeHandle.LIVENESS_ALIVE);    
        deadChecker = null;
      }
      if (needToNotifySCM) {
        scm.markAlive(address);
      }
    }
  }
  
  protected void rerouteQueueAndPendingAcks() {
    synchronized(pendingAcks) {
      Enumeration e = pendingAcks.keys();
      ArrayList list = new ArrayList();
      while (e.hasMoreElements()) {
        Object key = e.nextElement();
        AckTimeoutEvent ate = (AckTimeoutEvent)pendingAcks.get(key);
        if (rerouteMessage(ate.msg)) {
          list.add(key);
        }
      }
      Iterator i = list.iterator();
      while(i.hasNext()) {
        pendingAcks.remove(i.next());        
      }
    }
    
    synchronized(queue) {      
      Iterator i = queue.iterator();
      while (i.hasNext()) {
        if (rerouteMessage(((MsgEntry)i.next()).message)) {
          i.remove();
        }
      }
    }
  }
  
/*
  private void setDeadChecker(DeadChecker checker) {
    synchronized(livenessLock) {
      if ((deadChecker != null) && (checker != null)) {
        throw new RuntimeException("DeadChecker already exists");
      }
      deadChecker = checker;
    }
  }
*/

  /**
   * returns true if message should be rerouted
   * false means that the message needs to remain in queue until 
   * faulty
   * true means the message can be deleted from the queue
   */
  private boolean rerouteMessage(Message o) {    
    if (o != null) {
      // TODO: reroute ates.message
      //System.out.println("Should reroute "+o+" but dropping.");
      if (o instanceof SocketTransportMessage) {
        return rerouteExtractedMessage(((SocketTransportMessage)o).msg);
      } else {        
        return rerouteExtractedMessage(o);
      }
    } else {
      return true;
    }
  }
  
  private boolean rerouteExtractedMessage(Message o) {
    if (o instanceof RouteMessage) {
      switch (getLiveness()) {
        case NodeHandle.LIVENESS_SUSPECTED:
          RouteMessage rm = (RouteMessage)o;
          if (rm.getOptions().rerouteIfSuspected()) {
            scm.reroute(address, o);
            return true;
          } // else we are suspected, but not supposed to rerouteIfSuspected()
          return false;
        case NodeHandle.LIVENESS_FAULTY:
          scm.reroute(address, o);
          return true;        
        default: // alive
          return false; // not supposed to reroute if alive
      
      }            
    } else {
      switch (getLiveness()) {
        case NodeHandle.LIVENESS_SUSPECTED:
          return false; // don't throw out anything except route messages
        case NodeHandle.LIVENESS_FAULTY:
          return true; // throw out junk in the queue
        default:
          return false; // not supposed to reroute if alive
      }
    }
  }


  public String toString() {
    return "CM<"+scm.localAddress+">=><"+address+">";
  }

  private void debug(String s) {
    scm.debug(s);
  }


// ******************* inner classes ********************  
  class MsgEntry {
    public Message message;
    public long timeQueued;
    public MsgEntry(Message m) {
     this.message = m;
     timeQueued = System.currentTimeMillis(); 
    }
    public String toString() {
      if (message instanceof BroadcastLeafSet) {
        BroadcastLeafSet bls = (BroadcastLeafSet)message;
        return "  "+bls+" "+System.identityHashCode(bls)+" "+System.identityHashCode(bls.leafSet()) + " @"+timeQueued;            
      } else {
        return "  "+message+" "+System.identityHashCode(message) + " @"+timeQueued;
      }
    }
  }


  /**
   * DESCRIBE THE CLASS
   *
   * @version $Id$
   * @author jeffh
   */
  class DeadChecker extends TimerTask implements PingResponseListener {
    int tries = 1; // already called once
    int NUM_TRIES_SUSPECT_SUSPECT_FAULTY = 1; 
    int NUM_TRIES_DECLARE_FAULTY;
    InetSocketAddress address;
    PingManager manager;
    
    /**
     * Constructor for DeadChecker.
     *
     * @param address DESCRIBE THE PARAMETER
     * @param numTries DESCRIBE THE PARAMETER
     * @param mgr DESCRIBE THE PARAMETER
     */
    public DeadChecker(InetSocketAddress address, int declareDeadTries, PingManager mgr) {
      //System.out.println("Registered DeadChecker("+address+")");
      this.address = address;
      manager = mgr;
      this.NUM_TRIES_DECLARE_FAULTY = declareDeadTries;
    }

		/**
     * called by pingManager when the ping was received
     *
     * @param address DESCRIBE THE PARAMETER
     * @param RTT DESCRIBE THE PARAMETER
     * @param timeHeardFrom DESCRIBE THE PARAMETER
     */
    public void pingResponse(
                             InetSocketAddress address,
                             long RTT,
                             long timeHeardFrom) {
      //System.out.println("Terminated DeadChecker(" + address + ") due to ping.");
      markAlive();
    }

    /**
     * Main processing method for the DeadChecker object
     */
    public void run() {
      if (tries < NUM_TRIES_DECLARE_FAULTY) {
        //System.out.println("DeadChecker("+address+") pinging again."+tries);
        if (tries == NUM_TRIES_SUSPECT_SUSPECT_FAULTY) {
          markSuspected();
          tries++;
        } else {
          tries++;
        }
        manager.forcePing(address, this);
      } else {
        //System.out.println("DeadChecker("+address+") marking node dead.");        
        manager.doneProbing(address);
        markDead();
      }
    }

  }

    
  
  /**
   * DESCRIBE THE CLASS
   *
   * @version $Id$
   * @author jeffh
   */
  class AckTimeoutEvent extends TimerTask {
    public int ackNum;
    SocketTransportMessage msg;
    public long startTime;

    /**
     * Constructor for DeadChecker.
     *
     * @param address DESCRIBE THE PARAMETER
     * @param numTries DESCRIBE THE PARAMETER
     * @param mgr DESCRIBE THE PARAMETER
     */
    public AckTimeoutEvent(SocketTransportMessage msg) {
      this.ackNum = msg.seqNumber;
      this.msg = msg;
      startTime = System.currentTimeMillis();
    }

    /**
     * Main processing method for the DeadChecker object
     */
    public void run() {
      ackNotReceived(this);
    }

    public long elapsedTime() {
      return System.currentTimeMillis() - startTime;
    }
    
    public String toString() {
      return "ATE<"+ackNum+">:"+msg+":"+elapsedTime();
    }
  }



}

  


