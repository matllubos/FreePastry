 
package rice.pastry.wire;

import java.nio.channels.Selector;
import java.util.LinkedList;

/**
 * This is one of the first steps toward a centrally managed 
 * wire protocol that can support OS level limitations such
 * as FileDescriptor limits. This object has static lists 
 * of the sockets open to other connections.  This way such 
 * a list is maintained process wide since the number of 
 * FDs is a per-process count.
 * 
 * @author Jeff Hoye
 */
public class GlobalSocketManager extends SocketManager {

  static LinkedList openSocks = new LinkedList();

  static LinkedList pendingCloseSockets = new LinkedList();

	/**
   * Constructs a new GlobalSocketManager.
   * 
   * @param node The pastry node this manager is serving
   * @param port The port number which this manager is listening on
   * @param selector The Selector this manager should register with
	 */
	public GlobalSocketManager(WirePastryNode node, int port,
                             Selector selector) {
		super(node, port, selector);
	}

  /**
   * Returns the staticlly allocated list.
   */
  protected LinkedList generateOpenSockets() {
    return openSocks;
  }
  
  /**
   * asks the socketmanager wether to call 
   * removeOpenSocketsIfNeeded
   * 
   * @return wether to disconnect some sockets
   */
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
