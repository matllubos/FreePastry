/*
 *  Created on Mar 10, 2004
 *
 *  To change the template for this generated file go to
 *  Window>Preferences>Java>Code Generation>Code and Comments
 */
package rice.pastry.socket;

import java.net.InetSocketAddress;

/**
 * @version $Id$
 * @author jeffh To change the template for this generated type comment go to
 *      Window>Preferences>Java>Code Generation>Code and Comments
 */
public interface PingResponseListener {
  /**
   * DESCRIBE THE METHOD
   *
   * @param address DESCRIBE THE PARAMETER
   * @param RTT DESCRIBE THE PARAMETER
   * @param timeHeardFrom DESCRIBE THE PARAMETER
   */
  public void pingResponse(InetSocketAddress address, long RTT, long timeHeardFrom);
}
