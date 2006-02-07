package rice.pastry.direct;

import rice.environment.Environment;
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
   * Determines proximity between two nodes.
   * 
   * @param a a node id.
   * @param b another node id.
   * 
   * @return proximity of b to a.
   */

  public int proximity(DirectNodeHandle a, DirectNodeHandle b);

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
}
