/*
 * Created on Nov 8, 2005
 */
package rice.pastry.direct;

import java.nio.channels.Selector;
import java.util.*;

import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.environment.params.Parameters;
import rice.environment.random.RandomSource;
import rice.environment.random.simple.SimpleRandomSource;
import rice.environment.time.simulated.DirectTimeSource;
import rice.p2p.commonapi.CancellableTask;
import rice.pastry.*;
import rice.pastry.messaging.Message;
import rice.pastry.routing.BroadcastRouteRow;
import rice.selector.SelectorManager;
import rice.selector.TimerTask;

public abstract class BasicNetworkSimulator implements NetworkSimulator {

  Vector nodes = new Vector();

  // these messages should be delivered when the timer expires
  protected TreeSet taskQueue = new TreeSet();

  Environment environment;

  DirectTimeSource timeSource;

  private TestRecord testRecord;

  protected Logger logger;

  protected RandomSource random;

  protected int MIN_DELAY = 1;
  
  protected SelectorManager manager;
    
  public BasicNetworkSimulator(Environment env) {
    this.environment = env;
    manager = environment.getSelectorManager();
    Parameters params = env.getParameters();
    if (params.contains("pastry_direct_use_own_random")
        && params.getBoolean("pastry_direct_use_own_random")) {

      if (params.contains("pastry_direct_random_seed")
          && !params.getString("pastry_direct_random_seed").equalsIgnoreCase(
              "clock")) {
        this.random = new SimpleRandomSource(params
            .getLong("pastry_direct_random_seed"), env.getLogManager(),
            "direct");
      } else {
        this.random = new SimpleRandomSource(env.getLogManager(), "direct");
      }
    } else {
      this.random = env.getRandomSource();
    }
    this.logger = env.getLogManager().getLogger(getClass(), null);
    try {
      timeSource = (DirectTimeSource) env.getTimeSource();
    } catch (ClassCastException cce) {
      throw new IllegalArgumentException(
          "env.getTimeSource() must return a DirectTimeSource instead of a "
              + env.getTimeSource().getClass().getName());
    }
    testRecord = null;    
    start();
  }

  boolean running = false; // Invariant: only modified on the selector
  public void start() {
    // this makes things single threaded
    manager.invoke(new Runnable() {      
      public void run() {
        if (running) return;
        running = true;
        manager.invoke(new Runnable() {    
          public void run() {
            if (!running) return;            
            if (!simulate()) {
              Selector sel = manager.getSelector();
              synchronized(sel) {
                try {
                  sel.wait(100); // must wait on the real clock, because the simulated clock can only be advanced by simulate()
                } catch (InterruptedException ie) {
                  logger.logException("BasicNetworkSimulator interrupted.",ie); 
                }
              }
            }
            manager.invoke(this);
          }
        });
      }
    });
  }
    
  public void stop() {
    manager.invoke(new Runnable() {      
      public void run() {
        running = false;
      }
    });
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

  private void addTask(TimerTask dtt) {
    if (logger.level <= Logger.FINE) logger.log("addTask("+dtt+")");
//    System.out.println("addTask("+dtt+")");
    synchronized(taskQueue) {
      taskQueue.add(dtt);
    }
//    start();
//    if (!manager.isSelectorThread()) Thread.yield();
  }
  
  public CancellableTask enqueueDelivery(Delivery d, int delay) {
    long time = timeSource.currentTimeMillis()+delay;
    if (logger.level <= Logger.FINE)      
      logger.log("GNS: enqueueDelivery " + d+":"+time);
    DeliveryTimerTask dtt = null;
    dtt = new DeliveryTimerTask(d, time, d.getSeq());
    addTask(dtt);
    return dtt;
  }
  
  /**
   * node should always be a local node, because this will be delivered instantly
   */
  public ScheduledMessage deliverMessage(Message msg, DirectPastryNode node) {
    if (logger.level <= Logger.FINE)
      logger.log("GNS: deliver " + msg + " to " + node);
    DirectTimerTask dtt = null;
    if (msg.getSender() == null || msg.getSender().isAlive()) {
      MessageDelivery md = new MessageDelivery(msg, node);
      dtt = new DirectTimerTask(md, timeSource.currentTimeMillis());
      addTask(dtt);
    }
    return dtt;
  }

  public ScheduledMessage deliverMessage(Message msg, DirectPastryNode node,
      int delay) {
    if (logger.level <= Logger.FINE)
      logger.log("GNS: deliver("+delay+") " + msg + " to " + node);
    DirectTimerTask dtt = null;
    if (msg.getSender() == null || msg.getSender().isAlive()) {
      MessageDelivery md = new MessageDelivery(msg, node);
      dtt = new DirectTimerTask(md, timeSource.currentTimeMillis() + delay);
      addTask(dtt);
    }
    return dtt;
  }

  public ScheduledMessage deliverMessage(Message msg, DirectPastryNode node,
      int delay, int period) {
    DirectTimerTask dtt = null;
    if (msg.getSender() == null || msg.getSender().isAlive()) {
      MessageDelivery md = new MessageDelivery(msg, node);
      dtt = new DirectTimerTask(md, timeSource.currentTimeMillis() + delay,
          period);
      addTask(dtt);
    }
    return dtt;
  }

  public ScheduledMessage deliverMessageFixedRate(Message msg,
      DirectPastryNode node, int delay, int period) {
    DirectTimerTask dtt = null;
    if (msg.getSender() == null || msg.getSender().isAlive()) {
      MessageDelivery md = new MessageDelivery(msg, node);
      dtt = new DirectTimerTask(md, timeSource.currentTimeMillis() + delay,
          period, true);
      addTask(dtt);
    }
    return dtt;
  }

  /**
   * Delivers 1 message. Will advance the clock if necessary.
   * 
   * If there is a message in the queue, deliver that and return true. If there
   * is a message in the taskQueue, update the clock if necessary, deliver that,
   * then return true. If both are empty, return false;
   */  
  private boolean simulate() {
    if (!environment.getSelectorManager().isSelectorThread()) throw new RuntimeException("Must be on selector thread");
    
    TimerTask task;
    synchronized(taskQueue) {
      // take a task from the taskQueue
      if (taskQueue.isEmpty()) {
        if (logger.level <= Logger.FINE) logger.log("taskQueue is empty");
        return false;
      }
      task = (TimerTask) taskQueue.first();
      if (logger.level <= Logger.FINE) logger.log("simulate():"+task);
      taskQueue.remove(task);
    }
      // increment the clock if needed
      if (task.scheduledExecutionTime() > timeSource.currentTimeMillis()) {
        if (logger.level <= Logger.FINER) logger.log("the time is now "+task.scheduledExecutionTime());        
        timeSource.setTime(task.scheduledExecutionTime());
      }
  
  
      if (task.execute(timeSource)) {
        addTask(task);
      }    
      return true;
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
    Id theId;

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
