package rice.pastry.socket;

import java.net.InetSocketAddress;

/**
 * Interface which represents an object interested in hearing the result
 * of a ping.  The pingResponse() method will be called only if and when
 * a ping is heard back from.
 *
 * @version $Id$
 * @author amislove
 */
public interface PingResponseListener {
  
  /**
   * Method which is called once a previously-issued ping is
   * responded to.
   *
   * @param path The path of the ping
   * @param RTT The round-trip-time along the path
   * @param timeHeardFrom The time at which the response was received.
   */
  public void pingResponse(SourceRoute path, long RTT, long timeHeardFrom);
  
}
