/*
 * Created on May 12, 2004
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package rice.watchdog;

import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

import rice.pastry.socket.messaging.PingMessage;

/**
 * @author jeffh
 *
 * To change the template for this generated type comment go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
public class SocketPingMessageFactory implements PingMessageFactory {
  Serializable pingMessage = null;
  boolean initialized = false;
  int port;

  public SocketPingMessageFactory(int port) {
    this.port = port;    
  }

  private void initialize() throws UnknownHostException {
    if (initialized) return;
    initialized = true;
    pingMessage = null;
    // TODO: figure out how to fix this, now we need socket node handles to ping
//    pingMessage = new PingMessage(null, new InetSocketAddress(InetAddress.getLocalHost(),port));

/*
    try {
      Class clazz = Class.forName("rice.pastry.socket.messaging.PingMessage");
      Constructor[] ctors = clazz.getConstructors();
      pingMessage = (Serializable)ctors[0].newInstance(new Object[0]);        
    } catch (Exception e) {
      e.printStackTrace();
    }    
    */
  }
  
	public Serializable getPingMessage() {
    try {
      initialize();
    } catch (UnknownHostException uhe) {
      uhe.printStackTrace();
    }
		return pingMessage;
	}

	/* (non-Javadoc)
	 * @see rice.watchdog.PingMessageFactory#addressChanged()
	 */
	public void addressChanged() {
		initialized = false;		
	}

}
