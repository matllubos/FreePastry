
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
  
  public static int TYPE_TCP    = 0x00;
  public static int TYPE_UDP    = 0x01;
  public static int TYPE_SR_TCP = 0x10;
  public static int TYPE_SR_UDP = 0x11;
  
  public static int REASON_NORMAL = 0;
  public static int REASON_SR = 1;
  public static int REASON_BOOTSTRAP = 2;
  
  public static int REASON_ACC_NORMAL = 3;
  public static int REASON_ACC_SR = 4;
  public static int REASON_ACC_BOOTSTRAP = 5;
  public static int REASON_APP_SOCKET_NORMAL = 6;
  
  public void channelOpened(InetSocketAddress addr, int reason);
  public void channelClosed(InetSocketAddress addr);
  public void dataSent(int msgAddress, short msgType, InetSocketAddress socketAddress, int size, int wireType);
  public void dataReceived(int msgAddress, short msgType, InetSocketAddress socketAddress, int size, int wireType);
   
}


