/*
 * Created on Jan 30, 2006
 */
package rice.p2p.commonapi.appsocket;

/**
 * Interface to receive an application level socket.  This allows individual applications
 * to do their own bandwidth/flow control.  
 * 
 * 
 * @author Jeff Hoye
 */
public interface AppSocketReceiver {
  /**
   * Called when we have a new socket (due to a call to connect or accept)
   */
  void receiveSocket(AppSocket socket);
  /**
   * Called when a socket is available for read/write
   */
  void receiveSelectResult(AppSocket socket, boolean canRead, boolean canWrite);
  /**
   * Called when there is an error
   */
  void receiveException(AppSocket socket, Exception e); 
}
