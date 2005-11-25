package rice.pastry.direct;


import java.util.Hashtable;

import rice.environment.Environment;
import rice.pastry.*;
import rice.pastry.join.InitiateJoin;
import rice.pastry.messaging.Message;
import rice.selector.Timer;

/**
 * Direct pastry node. Subclasses PastryNode, and does about nothing else.
 * 
 * @version $Id$
 * 
 * @author Sitaram Iyer
 */

public class DirectPastryNode extends PastryNode {
  private NetworkSimulator simulator;
  protected boolean alive = true;
  NodeRecord record;
  
  protected Timer timer;

  public DirectPastryNode(NodeId id, NetworkSimulator sim, Environment e, NodeRecord nr) {
    super(id, e);
    timer = e.getSelectorManager().getTimer();
    simulator = sim;
    record = nr;
  }

  public void setDirectElements(/* simulator */) {
  }

  public void doneNode(NodeHandle bootstrap) {
    initiateJoin(bootstrap);
  }

  public boolean isAlive() {
    return alive; 
  }
  
  public void destroy() {
    super.destroy();
    this.alive = false; 
    simulator.removeNode(this);
  }
  
  /**
   * Sends an InitiateJoin message to itself.
   * 
   * @param bootstrap
   *          Node handle to bootstrap with.
   */
  public final void initiateJoin(NodeHandle bootstrap) {
    if (bootstrap != null)
      this.receiveMessage(new InitiateJoin(bootstrap));
    else
      setReady(); // no bootstrap node, so ready immediately
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
}

