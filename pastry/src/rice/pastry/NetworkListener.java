
package rice.pastry;

import java.net.*;

/**
 * Represents a listener to pastry network activity
 *
 * @version $Id$
 *
 * @author Peter Druschel
 */
public interface NetworkListener {
  
  public void dataSent(Object message, InetSocketAddress address, int size);
  public void dataReceived(Object message, InetSocketAddress address, int size);
   
}


