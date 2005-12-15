/*
 * Created on Nov 8, 2005
 */
package rice.pastry.direct;

import java.util.*;

import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.pastry.*;
import rice.pastry.messaging.Message;
import rice.pastry.routing.BroadcastRouteRow;

public abstract class GeometricNetworkSimulator implements NetworkSimulator {

  /**
   * Used for proximity calculation of DirectNodeHandle.  This will
   * probably go away when we switch to a byte-level protocol.
   */
  static DirectPastryNode currentNode = null;
  
  Vector nodes = new Vector();

  // these are messages that should be delivered immeadiately
  protected Vector msgQueue = new Vector();

  // these messages should be delivered when the timer expires
  protected TreeSet taskQueue = new TreeSet();

  Environment environment;

  DirectTimeSource timeSource;

  private TestRecord testRecord;
 
  protected Logger logger;
  
  public GeometricNetworkSimulator(Environment env) {
    this.environment = env;
    this.logger = env.getLogManager().getLogger(getClass(), null);
    try {
      timeSource = (DirectTimeSource) env.getTimeSource();
    } catch (ClassCastException cce) {
      throw new IllegalArgumentException(
          "env.getTimeSource() must return a DirectTimeSource instead of a "
              + env.getTimeSource().getClass().getName());
    }
    testRecord = null;
  }
  
  /**
   * get TestRecord
   *
   * @return the returned TestRecord
   */
  public TestRecord getTestRecord() {
    return testRecord;
  }

  
  /**
   * set TestRecord
   *
   * @param tr input TestRecord
   */
  public void setTestRecord(TestRecord tr) {
    testRecord = tr;
  }


  public void deliverMessage(Message msg, DirectPastryNode node) {
    if (logger.level <= Logger.FINE) logger.log(
        "GNS: deliver "+msg+" to "+node);
    if (msg.getSender() == null || msg.getSender().isAlive()) {
      MessageDelivery md = new MessageDelivery(msg, node);
      msgQueue.addElement(md);
    }
  }

  public ScheduledMessage deliverMessage(Message msg, DirectPastryNode node, int delay) {
    DirectTimerTask dtt = null;
    if (msg.getSender().isAlive()) {
      MessageDelivery md = new MessageDelivery(msg, node);
      dtt = new DirectTimerTask(md,timeSource.currentTimeMillis()+delay);
      taskQueue.add(dtt);
    }
    return dtt;
  }
  
  public ScheduledMessage deliverMessage(Message msg, DirectPastryNode node, int delay, int period) {
    DirectTimerTask dtt = null;
    if (msg.getSender().isAlive()) {
      MessageDelivery md = new MessageDelivery(msg, node);
      dtt = new DirectTimerTask(md,timeSource.currentTimeMillis()+delay, period);
      taskQueue.add(dtt);
    }
    return dtt;
  }
  
  public ScheduledMessage deliverMessageFixedRate(Message msg, DirectPastryNode node, int delay, int period) {
    DirectTimerTask dtt = null;
    if (msg.getSender().isAlive()) {
      MessageDelivery md = new MessageDelivery(msg, node);
      dtt = new DirectTimerTask(md,timeSource.currentTimeMillis()+delay, period, true);
      taskQueue.add(dtt);
    }
    return dtt;
  }
  
  public boolean simulate() {
    return simulate(Long.MAX_VALUE);
  }
  
  /**
   * Delivers 1 message. Will advance the clock if necessary.
   * 
   * If there is a message in the queue, deliver that and return true. If there
   * is a message in the taskQueue, update the clock if necessary, deliver that,
   * then return true. If both are empty, return false;
   */
  protected boolean simulate(long maxTime) {
    if (msgQueue.isEmpty() && taskQueue.isEmpty()) {
      return false;
    }

    // take a message from the msgQueue if possible
    if (!msgQueue.isEmpty()) {
      MessageDelivery md = (MessageDelivery) msgQueue.firstElement();

      msgQueue.removeElementAt(0);

      md.deliver();

      return true;
    } else {
      // take a task from the taskQueue
      DirectTimerTask task = (DirectTimerTask) taskQueue.first();
      if (task.scheduledExecutionTime() > maxTime) {
        return false;
      }
      // increment the clock if needed
      if (task.scheduledExecutionTime() > timeSource.currentTimeMillis()) {
        timeSource.setTime(task.scheduledExecutionTime());
      }

      taskQueue.remove(task);

      if (task.execute(timeSource)) {
        taskQueue.add(task);
      }
      return true;
    }
  }

  public boolean simulateFor(int millis) {
    return simulateUntil(timeSource.currentTimeMillis()+millis);
  }
  
  
  /**
   * 1) process the msgQueue
   * 2) see if there is 
   * a) no scheduled messages between now and then
   */
  public boolean simulateUntil(long targetTime) {    
    if (msgQueue.isEmpty() && taskQueue.isEmpty()) {
      return false;
    }
        
    // This loop looks hairy, but the idea is that it calls simulate with targetTime until it returns false.
    // if it ever returns true, then ret = true;
    boolean ret = false;
    while(simulate(targetTime)) {
      ret = true; 
    }
    timeSource.setTime(targetTime);
    return ret;
  }

  
  
  /**
   * testing if a NodeId is alive
   * 
   * @param nid the NodeId being tested
   * @return true if nid is alive false otherwise
   */
  public boolean isAlive(DirectNodeHandle nh) {
    return nh.getRemote().isAlive();
  }

  /**
   * set the liveliness of a NodeId
   * 
   * @param nid the NodeId being set
   * @param alive the value being set
   */
  public void destroy(DirectPastryNode node) {
    node.destroy();
    // NodeRecord nr = (NodeRecord) nodeMap.get(nid);
    //
    // if (nr == null) {
    // throw new Error("setting node alive for unknown node");
    // }
    //
    // if (nr.alive != alive) {
    // nr.alive = alive;
    //
    // DirectNodeHandle[] handles = (DirectNodeHandle[]) nr.handles.toArray(new
    // DirectNodeHandle[0]);
    //
    // for (int i = 0; i < handles.length; i++) {
    // if (alive) {
    // handles[i].notifyObservers(NodeHandle.DECLARED_LIVE);
    // } else {
    // handles[i].notifyObservers(NodeHandle.DECLARED_DEAD);
    // }
    // }
    // }
  }

  /**
   * computes the proximity between two NodeIds
   * 
   * @param a the first NodeId
   * @param b the second NodeId
   * @return the proximity between the two input NodeIds
   */
  public int proximity(DirectNodeHandle a, DirectNodeHandle b) {
    NodeRecord nra = a.getRemote().record;    
    NodeRecord nrb = b.getRemote().record;

    if (nra == null || nrb == null) {
      throw new Error("asking about node proximity for unknown node(s)");
    }

    return nra.proximity(nrb);
  }

  /**
   * find the closest NodeId to an input NodeId out of all NodeIds in the
   * network
   * 
   * @param nid the input NodeId
   * @return the NodeId closest to the input NodeId in the network
   */
  public DirectNodeHandle getClosest(DirectNodeHandle nh) {
    Iterator it = nodes.iterator();
    DirectNodeHandle bestHandle = null;
    int bestProx = Integer.MAX_VALUE;
    NodeId theId;

    while (it.hasNext()) {
      DirectPastryNode theNode = (DirectPastryNode) it.next();
      int theProx = theNode.record.proximity(nh.getRemote().record);
      theId = theNode.getNodeId();
      if (!theNode.isAlive() || !theNode.isReady()
          || theId.equals(nh.getNodeId())) {
        continue;
      }

      if (theProx < bestProx) {
        bestProx = theProx;
        bestHandle = (DirectNodeHandle) theNode.getLocalHandle();
      }
    }
    return bestHandle;
  }



  public void registerNode(DirectPastryNode dpn) {
    nodes.add(dpn);
  }



  public void removeNode(DirectPastryNode node) {
    nodes.remove(node);
  }

  public Environment getEnvironment() {
    return environment;
  }

}
