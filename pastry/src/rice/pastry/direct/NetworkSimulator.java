package rice.pastry.direct;

import rice.environment.Environment;
import rice.p2p.commonapi.CancellableTask;
import rice.pastry.*;
import rice.pastry.messaging.*;

/**
 * Interface to an object which is simulating the network.
 * 
 * @version $Id$
 * 
 * @author Andrew Ladd
 */

public interface NetworkSimulator {

  public Environment getEnvironment();
  
  /**
   * Registers a node handle with the simulator.
   * 
   * @param nh the node handle to register.
   */
  public void registerNode(DirectPastryNode dpn);

  /**
   * Checks to see if a node id is alive.
   * 
   * @param nid a node id.
   * 
   * @return true if alive, false otherwise.
   */

  public boolean isAlive(DirectNodeHandle nh);

  /**
   * Determines rtt between two nodes.
   * 
   * @param a a node id.
   * @param b another node id.
   * 
   * @return proximity of b to a.
   */
  public float proximity(DirectNodeHandle a, DirectNodeHandle b);
  
  /**
   * Determines delivery time from a to b.
   * 
   * @param a a node id.
   * @param b another node id.
   * 
   * @return proximity of b to a.
   */
  public float networkDelay(DirectNodeHandle a, DirectNodeHandle b);
  
  /**
   * Deliver message.
   * 
   * @param msg message to deliver.
   * @param node the Pastry node to deliver it to.
   * @param how long to delay to deliver the message
   */
  public ScheduledMessage deliverMessage(Message msg, DirectPastryNode node, int delay);

  /**
   * Deliver message.
   * 
   * @param msg message to deliver.
   * @param node the Pastry node to deliver it to.
   * @param how long to delay to deliver the message
   * @param period to deliver the message after the delay
   */
  public ScheduledMessage deliverMessage(Message msg, DirectPastryNode node, int delay, int period);

  /**
   * Deliver message.
   * 
   * @param msg message to deliver.
   * @param node the Pastry node to deliver it to.
   * @param how long to delay to deliver the message
   * @param period to deliver the message after the delay
   */
  public ScheduledMessage deliverMessageFixedRate(Message msg, DirectPastryNode node, int delay, int period);

  /**
   * Deliver message ASAP.
   * 
   * @param msg message to deliver.
   * @param node the Pastry node to deliver it to.
   */
  public ScheduledMessage deliverMessage(Message msg, DirectPastryNode node);

  public void setTestRecord(TestRecord tr);

  public TestRecord getTestRecord();

  /**
   * Returns the closest Node in proximity.
   * 
   * @param nid
   * @return
   */
  public DirectNodeHandle getClosest(DirectNodeHandle nh);

  public void destroy(DirectPastryNode dpn);

  /**
   * Generates a random node record
   * 
   * @return
   */
  public NodeRecord generateNodeRecord();

  public void removeNode(DirectPastryNode node);

  public void start();
  
  public void stop();
  
  /**
   * Deliver message.
   * 
   * @param msg message to deliver.
   * @param node the Pastry node to deliver it to.
   * @param how long to delay to deliver the message
   * @param period to deliver the message after the delay
   */
//  public CancellableTask enqueueDelivery(Delivery del);  
  public CancellableTask enqueueDelivery(Delivery del, int delay);

  /**
   * The max rate of the simulator compared to realtime. 
   * 
   * The rule is that the simulated clock will not be set to a value greater 
   * than the factor from system-time that the call was made.  Thus
   * 
   * if 1 hour ago, you said the simulator should run at 10x realtime the simulated
   * clock will only have advanced 10 hours.  
   * 
   * Note that if the simulator cannot keep up with the system clock in the early 
   * part, it may move faster than the value you set to "catch up" 
   * 
   * To prevent this speed-up from becoming unbounded, you may wish to call
   * setMaxSpeed() periodically or immediately after periods of expensive calculations.
   * 
   * Setting the simulation speed to zero will not pause the simulation, you must 
   * call stop() to do that.
   * 
   * @param the multiple on realtime that the simulator is allowed to run at, 
   * zero or less will cause no bound on the simulation speed
   * 
   */
  public void setMaxSpeed(float rate);

  /**
   * unlimited maxSpeed
   *
   */
  public void setFullSpeed();
}
