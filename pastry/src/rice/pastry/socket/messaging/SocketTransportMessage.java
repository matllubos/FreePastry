/*
 * Created on Mar 25, 2004
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package rice.pastry.socket.messaging;

import rice.pastry.messaging.Message;

/**
 * @author jeffh
 *
 * To change the template for this generated type comment go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
public class SocketTransportMessage extends SocketMessage {

  public Message msg;

	/**
	 * 
	 */
	public SocketTransportMessage(Message msg, int seq) {
    this.msg = msg;
    this.seqNumber = seq;
	}
  
  public boolean hasPriority() {
    return msg.hasPriority();
  }

  public String toString() {
    return "["+msg.toString()+"]";
  }

}
