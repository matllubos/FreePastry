package rice.pastry.direct;

import org.mpisws.p2p.transport.TransportLayer;
import org.mpisws.p2p.transport.liveness.LivenessProvider;
import org.mpisws.p2p.transport.proximity.ProximityProvider;

import rice.environment.Environment;
import rice.environment.random.RandomSource;
import rice.p2p.commonapi.Cancellable;
import rice.p2p.commonapi.CancellableTask;
import rice.pastry.ScheduledMessage;

public interface GenericNetworkSimulator<Identifier, MessageType> extends LivenessProvider<Identifier> {

  public Environment getEnvironment();

  /**
   * Determines delivery time from a to b.
   * 
   * @param a a node id.
   * @param b another node id.
   * 
   * @return delay of b from a.
   */
  public float networkDelay(Identifier a, Identifier b);

  
  /**
   * Deliver message.
   * 
   * @param msg message to deliver.
   * @param node the Pastry node to deliver it to.
   * @param how long to delay to deliver the message
   */
  public Cancellable deliverMessage(MessageType msg, Identifier to, Identifier from, int delay);

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
   
  public DirectTransportLayer<Identifier, MessageType> getTL(Identifier i);

  public boolean isAlive(Identifier i);
  
  /**
   * Kill identifier.
   * @param i
   */
  public void remove(Identifier i);
  

  public void start();
  
  public void stop();

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

  public RandomSource getRandomSource();

}
