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
import rice.environment.time.TimeSource;
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
//  protected TreeSet taskQueue = new TreeSet();

  Environment environment;

  TimeSource timeSource;

  // true if we are responsible for incrementing the time
  private boolean isDirectTimeSource = false;
  
  private TestRecord testRecord;

  protected Logger logger;

  protected RandomSource random;

  protected int MIN_DELAY = 1;
  
  protected SelectorManager manager;
    
  protected final int maxDiameter;
  protected final int minDelay;
  
  public BasicNetworkSimulator(Environment env) {
    this.environment = env;
    manager = environment.getSelectorManager();
    manager.useLoopListeners(false);
    Parameters params = env.getParameters();
    maxDiameter = params.getInt("pastry_direct_max_diameter");
    minDelay = params.getInt("pastry_direct_min_delay");
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
//    try {
    timeSource = env.getTimeSource();
//    } catch (ClassCastException cce) {
//      throw new IllegalArgumentException(
//          "env.getTimeSource() must return a DirectTimeSource instead of a "
//              + env.getTimeSource().getClass().getName());
//    }
    if (timeSource instanceof DirectTimeSource)
      isDirectTimeSource = true;
    manager.setSelect(false);
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
            try {
            if (!simulate()) {
              synchronized(manager) {
                try {
                  manager.wait(100); // must wait on the real clock, because the simulated clock can only be advanced by simulate()
                } catch (InterruptedException ie) {
                  logger.logException("BasicNetworkSimulator interrupted.",ie); 
                }
              }
            }
            // re-invoke the simulation task
            manager.invoke(this);
            } catch (InterruptedException ie) {
              if (logger.level <= Logger.SEVERE) logger.logException("BasicNetworkSimulator.start()",ie); 
              stop();
            }
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
//    synchronized(taskQueue) {
//      taskQueue.add(dtt);
//    }
    manager.getTimer().schedule(dtt);
//    start();
//    if (!manager.isSelectorThread()) Thread.yield();
  }
  
  public CancellableTask enqueueDelivery(Delivery d, int delay) {
    long time = timeSource.currentTimeMillis()+delay;
    if (logger.level <= Logger.FINE)      
      logger.log("BNS: enqueueDelivery " + d+":"+time);
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
      logger.log("BNS: deliver " + msg + " to " + node);
    DirectTimerTask dtt = null;
    
    DirectNodeHandle sender = null;
    sender = (DirectNodeHandle)msg.getSender();
//    DirectPastryNode senderNode = DirectPastryNode.getCurrentNode();
//    if (senderNode != null)
//      sender = (DirectNodeHandle)senderNode.getLocalNodeHandle();
    if (sender == null || sender.isAlive()) {
      MessageDelivery md = new MessageDelivery(msg, node);
      dtt = new DirectTimerTask(md, timeSource.currentTimeMillis());
      addTask(dtt);
    }
    return dtt;
  }

  public ScheduledMessage deliverMessage(Message msg, DirectPastryNode node,
      int delay) {
    if (logger.level <= Logger.FINE)
      logger.log("BNS: deliver("+delay+") " + msg + " to " + node);
    DirectTimerTask dtt = null;
    
    DirectNodeHandle sender = null;
    sender = (DirectNodeHandle)msg.getSender();
//    DirectPastryNode senderNode = DirectPastryNode.getCurrentNode();
//    if (senderNode != null)
//      sender = (DirectNodeHandle)senderNode.getLocalNodeHandle();
    if (sender == null || sender.isAlive()) {
      MessageDelivery md = new MessageDelivery(msg, node);
      dtt = new DirectTimerTask(md, timeSource.currentTimeMillis() + delay);
      addTask(dtt);
    }
    return dtt;
  }

  public ScheduledMessage deliverMessage(Message msg, DirectPastryNode node,
      int delay, int period) {
    DirectTimerTask dtt = null;
    
    DirectNodeHandle sender = null;
    sender = (DirectNodeHandle)msg.getSender();
//    DirectPastryNode senderNode = DirectPastryNode.getCurrentNode();
//    if (senderNode != null)
//      sender = (DirectNodeHandle)senderNode.getLocalNodeHandle();
    if (sender == null || sender.isAlive()) {
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

    DirectNodeHandle sender = null;
    sender = (DirectNodeHandle)msg.getSender();
//    DirectPastryNode senderNode = DirectPastryNode.getCurrentNode();
//    if (senderNode != null)
//      sender = (DirectNodeHandle)senderNode.getLocalNodeHandle();
    if (sender == null || sender.isAlive()) {
      MessageDelivery md = new MessageDelivery(msg, node);
      dtt = new DirectTimerTask(md, timeSource.currentTimeMillis() + delay,
          period, true);
      addTask(dtt);
    }
    return dtt;
  }

  // System Time is the system clock
  // Sim Time is the simulated clock
  long maxSpeedRequestSystemTime = 0;
  long maxSpeedRequestSimTime = 0;
  float maxSpeed = 0.0f;
  
  /**
   * This is a guardian for printing the "Invalid TimeSource" warning.  So that it is only printed once.
   */
  boolean printedDirectTimeSourceWarning = false;
  
  public void setMaxSpeed(float speed) {
    if (!isDirectTimeSource) {
      if (!printedDirectTimeSourceWarning) {
        if (logger.level <= Logger.WARNING) logger.log("Invalid TimeSource for setMaxSpeed()/setFullSpeed().  Use Environment.directEnvironment() to construct your Environment.");
        printedDirectTimeSourceWarning = true;
      }
    }
    maxSpeedRequestSystemTime = System.currentTimeMillis();
    maxSpeedRequestSimTime = timeSource.currentTimeMillis();
    maxSpeed = speed;
  }
  
  public void setFullSpeed() {
    setMaxSpeed(-1.0f);
  }
  
  /**
   * Delivers 1 message. Will advance the clock if necessary.
   * 
   * If there is a message in the queue, deliver that and return true. If there
   * is a message in the taskQueue, update the clock if necessary, deliver that,
   * then return true. If both are empty, return false;
   */  
  private boolean simulate() throws InterruptedException {
    if (!isDirectTimeSource) return true;
    if (!environment.getSelectorManager().isSelectorThread()) throw new RuntimeException("Must be on selector thread");
    synchronized(manager) { // so we can wait on it, and so the clock and nextExecution don't change
      
      long scheduledExecutionTime = manager.getNextTaskExecutionTime();
      if (scheduledExecutionTime < 0) {
        if (logger.level <= Logger.FINE) logger.log("taskQueue is empty");
        return false;
      }
      
      if (scheduledExecutionTime > timeSource.currentTimeMillis()) {
        long newSimTime = scheduledExecutionTime;
        if (maxSpeed > 0) {
          long sysTime = System.currentTimeMillis();
          long sysTimeDiff = sysTime-maxSpeedRequestSystemTime;
          
          long maxSimTime = (long)(maxSpeedRequestSimTime+(sysTimeDiff*maxSpeed));
          
          if (maxSimTime < newSimTime) {
            // we need to throttle
            long neededSysDelay = (long)((newSimTime-maxSimTime)/maxSpeed);
//            System.out.println("Waiting for "+neededSysDelay);
            if (neededSysDelay >= 1) {
              manager.wait(neededSysDelay);          
              long now = System.currentTimeMillis();
              long delay = now-sysTime;
  //            System.out.println("Woke up after "+delay);
              if (delay < neededSysDelay) return true;
            }
          }
        }
          
        if (logger.level <= Logger.FINER) logger.log("the time is now "+newSimTime);              
        ((DirectTimeSource)timeSource).setTime(newSimTime);      
      }
    } // synchronized(manager)
    
//    TimerTask task;
//    synchronized(taskQueue) {
//      // take a task from the taskQueue
//      if (taskQueue.isEmpty()) {
//        if (logger.level <= Logger.FINE) logger.log("taskQueue is empty");
//        return false;
//      }
//      task = (TimerTask) taskQueue.first();
//      if (logger.level <= Logger.FINE) logger.log("simulate():"+task);
//      taskQueue.remove(task);
//    }
//      // increment the clock if needed
//      if (task.scheduledExecutionTime() > timeSource.currentTimeMillis()) {
//        if (logger.level <= Logger.FINER) logger.log("the time is now "+task.scheduledExecutionTime());        
//        timeSource.setTime(task.scheduledExecutionTime());
//      }
//  
//  
//      if (task.execute(timeSource)) {
//        addTask(task);
//      }    
      
      
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
  public float networkDelay(DirectNodeHandle a, DirectNodeHandle b) {
    NodeRecord nra = a.getRemote().record;
    NodeRecord nrb = b.getRemote().record;

    if (nra == null || nrb == null) {
      throw new Error("asking about node proximity for unknown node(s)");
    }

    return nra.networkDelay(nrb);
  }

  public float proximity(DirectNodeHandle a, DirectNodeHandle b) {
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
    float bestProx = Float.MAX_VALUE;
    Id theId;

    while (it.hasNext()) {
      DirectPastryNode theNode = (DirectPastryNode) it.next();
      float theProx = theNode.record.proximity(nh.getRemote().record);
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
