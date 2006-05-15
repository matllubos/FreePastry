/*
 * Created on Apr 21, 2006
 */
package rice.p2p.past.rawserialization;

import rice.p2p.past.PastContent;

/**
 * Past should send larger messages along the Applicaion Level Sockets to not 
 * interfere with Pastry's overlay maintenance, and other application traffic.
 * 
 * @author Jeff Hoye
 */
public interface SocketStrategy {
  public static final int TYPE_INSERT = 1;
  public static final int TYPE_FETCH = 2;
  
  
  /**
   * Return true to send the content along a socket, false to send it as a message.
   * 
   * @param sendType // the reason the content is being transmitted: TYPE_INSERT, TYPE_FETCH etc
   * @param content
   * @return
   */
  public boolean sendAlongSocket(int sendType, PastContent content);
}
