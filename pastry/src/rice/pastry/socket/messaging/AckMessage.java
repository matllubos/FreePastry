/*
 * Created on Mar 25, 2004
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package rice.pastry.socket.messaging;

/**
 * @author jeffh
 *
 * To change the template for this generated type comment go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
public class AckMessage extends SocketMessage {

	/**
	 * 
	 */
	public AckMessage(int seqNum) {
		super();
    seqNumber = seqNum;    
    setPriority(true);
	}

}
