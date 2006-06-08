package rice.pastry.direct;


import java.util.Hashtable;

import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.p2p.commonapi.*;
import rice.p2p.commonapi.appsocket.AppSocketReceiver;
import rice.p2p.commonapi.rawserialization.InputBuffer;
import rice.pastry.*;
import rice.pastry.Id;
import rice.pastry.NodeHandle;
import rice.pastry.client.PastryAppl;
import rice.pastry.join.InitiateJoin;
import rice.pastry.messaging.Message;
import rice.pastry.routing.RouteMessage;
import rice.selector.Timer;

/**
 * Direct pastry node. Subclasses PastryNode, and does about nothing else.
 * 
 * @version $Id$
 * 
 * @author Sitaram Iyer
 */

public class DirectPastryNode extends PastryNode {
  /**
   * Used for proximity calculation of DirectNodeHandle. This will probably go
   * away when we switch to a byte-level protocol.
   */
  static public DirectPastryNode currentNode = null;

  private NetworkSimulator simulator;
  protected boolean alive = true;
  NodeRecord record;
  protected Timer timer;
  // used to control message order for messages destined to arrive at the same time
  int seq = 0;

  public DirectPastryNode(Id id, NetworkSimulator sim, Environment e, NodeRecord nr) {
    super(id, e);
    timer = e.getSelectorManager().getTimer();
    simulator = sim;
    record = nr;
  }

  public void doneNode(NodeHandle bootstrap) {
    initiateJoin(bootstrap);
  }

  public boolean isAlive() {
    return alive; 
  }
  
  public void destroy() {
    super.destroy();
    alive = false;
    setReady(false); 
    simulator.removeNode(this);
  }
  
  
  public final void initiateJoin(NodeHandle bootstrap) {
    NodeHandle[] boots = new NodeHandle[1];
    boots[0] = bootstrap;
    initiateJoin(boots);
  }
  
  /**
   * Sends an InitiateJoin message to itself.
   * 
   * @param bootstrap
   *          Node handle to bootstrap with.
   */
  public final void initiateJoin(NodeHandle[] bootstrap) {
    if (bootstrap != null && bootstrap[0] != null) {
      simulator.deliverMessage(new InitiateJoin(bootstrap), this);
//      this.receiveMessage(new InitiateJoin(bootstrap));
    } else {
      setReady(); // no bootstrap node, so ready immediately
    }
  }

  /**
   * Called from PastryNode after the join succeeds.
   */
  public final void nodeIsReady() {
  }

  /**
   * Schedule the specified message to be sent to the local node after a
   * specified delay. Useful to provide timeouts.
   * 
   * @param msg
   *          a message that will be delivered to the local node after the
   *          specified delay
   * @param delay
   *          time in milliseconds before message is to be delivered
   * @return the scheduled event object; can be used to cancel the message
   */
  public ScheduledMessage scheduleMsg(Message msg, long delay) {
    return simulator.deliverMessage(msg, this, (int)delay);
  }

  /**
   * Schedule the specified message for repeated fixed-delay delivery to the
   * local node, beginning after the specified delay. Subsequent executions take
   * place at approximately regular intervals separated by the specified period.
   * Useful to initiate periodic tasks.
   * 
   * @param msg
   *          a message that will be delivered to the local node after the
   *          specified delay
   * @param delay
   *          time in milliseconds before message is to be delivered
   * @param period
   *          time in milliseconds between successive message deliveries
   * @return the scheduled event object; can be used to cancel the message
   */
  public ScheduledMessage scheduleMsg(Message msg, long delay, long period) {
    return simulator.deliverMessage(msg, this, (int)delay, (int)period);
  }

  /**
   * Schedule the specified message for repeated fixed-rate delivery to the
   * local node, beginning after the specified delay. Subsequent executions take
   * place at approximately regular intervals, separated by the specified
   * period.
   * 
   * @param msg
   *          a message that will be delivered to the local node after the
   *          specified delay
   * @param delay
   *          time in milliseconds before message is to be delivered
   * @param period
   *          time in milliseconds between successive message deliveries
   * @return the scheduled event object; can be used to cancel the message
   */
  public ScheduledMessage scheduleMsgAtFixedRate(Message msg, long delay,
      long period) {
    return simulator.deliverMessageFixedRate(msg, this, (int)delay, (int)period);
  }

  Hashtable nodeHandles = new Hashtable();
  public NodeHandle coalesce(NodeHandle newHandle) {
    NodeHandle ret = (NodeHandle)nodeHandles.get(newHandle);
    if (ret == null) {
      nodeHandles.put(newHandle, newHandle); 
      ret = newHandle;
    }
    return ret;
  }

  public synchronized void receiveMessage(Message msg) {
//    System.out.println("setting currentNode from "+currentNode+" to "+this+" on "+Thread.currentThread());   
    if (!getEnvironment().getSelectorManager().isSelectorThread()) {
      simulator.deliverMessage(msg, this); 
      return;
    }
    
    DirectPastryNode temp = currentNode;
//    if ((currentNode != null) && (currentNode != this))
//      throw new RuntimeException("receiveMessage called recursively!");
//    System.out.println("currentNode != null");
    currentNode = this;
    super.receiveMessage(msg);
    currentNode = temp;
  }

  public synchronized void route(RouteMessage rm) {
    if (!getEnvironment().getSelectorManager().isSelectorThread()) {
      simulator.deliverMessage(rm, this); 
      return;
    }
    
    DirectPastryNode temp = currentNode;
    currentNode = this;
    super.receiveMessage(rm);
    currentNode = temp;
  }
  
  public Logger getLogger() {
    return logger;
  }

  public void send(NodeHandle handle, Message message) {
    handle.receiveMessage(message);
  }
  
  public void connect(NodeHandle remoteNode, AppSocketReceiver receiver, PastryAppl appl, int timeout) {
    DirectNodeHandle dnh = (DirectNodeHandle)remoteNode;
    simulator.enqueueDelivery(new DirectAppSocket(dnh, receiver, appl, simulator).getAcceptorDelivery(),
        simulator.proximity((DirectNodeHandle)localhandle, dnh));
  }

  public NodeHandle readNodeHandle(InputBuffer buf) {
    throw new RuntimeException("Should not be called.");
  }
}

