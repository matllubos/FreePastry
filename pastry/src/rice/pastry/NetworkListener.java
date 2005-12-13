
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
  
  public static int TYPE_UDP    = 0x001;
  public static int TYPE_TCP    = 0x010;
  public static int TYPE_SR_UDP = 0x101;
  public static int TYPE_SR_TCP = 0x110;
  
  public static int REASON_NORMAL = 0;
  public static int REASON_SR = 1;
  public static int REASON_BOOTSTRAP = 2;
  
  public static int REASON_ACC_NORMAL = 3;
  public static int REASON_ACC_SR = 4;
  public static int REASON_ACC_BOOTSTRAP = 5;
  
  public void channelOpened(InetSocketAddress addr, int reason);
  public void channelClosed(InetSocketAddress addr);
  public void dataSent(Object message, InetSocketAddress address, int size, int type);
  public void dataReceived(Object message, InetSocketAddress address, int size, int type);
   
}


