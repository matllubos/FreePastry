/*
 * Created on Jan 14, 2004
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package rice.pastry.wire;

import java.nio.channels.Selector;
import java.util.LinkedList;

/**
 * @author jeffh
 *
 * To change the template for this generated type comment go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
public class GlobalSocketManager extends SocketManager {

  static LinkedList openSocks = new LinkedList();

  static LinkedList pendingCloseSockets = new LinkedList();

	/**
	 * @param node
	 * @param port
	 * @param selector
	 */
	public GlobalSocketManager(WirePastryNode node, int port,
                             Selector selector) {
		super(node, port, selector);
	}

  protected LinkedList generateOpenSockets() {
    return openSocks;
  }
  
  protected boolean needToDisconnectSockets() {
    return Wire.needToReleaseFDs();
  }  
  /*
  protected void removeOpenSocketsIfNeeded() {
    while (needToDisconnectSockets() && openSockets.size() > 0) {             
      WireNodeHandle snh = (WireNodeHandle) openSockets.getLast();
      pendingCloseSockets.add(snh);      
      System.out.println("Removing Open Soket because needed. :"+snh.getAddress());   
      snh.disconnect();
    }    
  }
 */
}
