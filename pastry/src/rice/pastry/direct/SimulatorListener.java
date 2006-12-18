/*
 * Created on Dec 18, 2006
 */
package rice.pastry.direct;

import rice.p2p.commonapi.*;

/**
 * Notified for every message that is sent over the network.
 * 
 * @author Jeff Hoye
 */
public interface SimulatorListener {
  
  /**
   * Called for every message sent over the network.
   * 
   * @param m the Message that was sent.
   * @param from the source.
   * @param to the destination
   * @param delay when the message will be delivered (in millis)
   */
  public void messageSent(Message m, NodeHandle from, NodeHandle to, int delay);
}
