/*
 * Created on Mar 25, 2004
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package rice.pastry.socket.messaging;

import java.net.InetSocketAddress;

import rice.pastry.NodeId;
import rice.pastry.socket.SocketNodeHandle;

/**
 * @author jeffh
 *
 * To change the template for this generated type comment go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
public class AddressMessage extends SocketControlMessage {

  public SocketNodeHandle sender;
  public SocketNodeHandle receiver;
  public int type;
  
  
	/**
	 * @param address
	 */
	public AddressMessage(SocketNodeHandle sender, SocketNodeHandle receiver, int type) {
    this.sender = sender;
    this.receiver = receiver;
    this.type = type;
	}
  
  

	public boolean hasPriority() {
		return true;
	}

}
