/*
 *  Created on Mar 10, 2004
 *
 *  To change the template for this generated file go to
 *  Window>Preferences>Java>Code Generation>Code and Comments
 */
package rice.pastry.socket;

import java.net.InetSocketAddress;

/**
 * Interface for listening to pings.  You may want to read the PingManager documentation.
 * 
 * Ping response may be called with cached values if there was a ping recently.
 * 
 * @author Jeff Hoye
 */
public interface PingResponseListener {
  /**
   * Called upon reception of a PingResponse, or called with cached values
   * if a ping happended recently (less than PingManager.PING_THROTTLE time ago).
   *
   * @param address the address you pinged
   * @param RTT the rtt of the most recent ping 
   * @param timeHeardFrom the time we got that response
   */
  public void pingResponse(InetSocketAddress address, long RTT, long timeHeardFrom);
}
