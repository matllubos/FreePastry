/*
 * Created on Mar 31, 2004
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package rice.pastry.socket;

import java.util.LinkedList;

/**
 * @author jeffh
 *
 * To change the template for this generated type comment go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
public class SocketPoolManager {

  // the linked list of open sockets
  private LinkedList queue;
  SocketCollectionManager scm;

	public SocketPoolManager(SocketCollectionManager scm) {
    this.scm = scm;
    queue = new LinkedList();    
	}

  /**
   * Method which is designed to be called whenever a node has network activity.
   * This is used to determine which nodes should be disconnected, should it be
   * necessary (implementation of a LRU stack).
   *
   * @param address DESCRIBE THE PARAMETER
   */
  protected void socketUpdated(SocketManager sm) {
    synchronized(queue) {
      queue.remove(sm);
      queue.addFirst(sm);
    }
  }


//  protected void socketUpdated(InetSocketAddress address, int type) {
  /*
  protected void socketUpdated(InetSocketAddress address, int type) {
    if (type == TYPE_DATA) {
      synchronized (dataSockets) {
        SocketManager sm = (SocketManager)dataSockets.get(address);
        if (sm != null) {
          queue.remove(sm);
          queue.addFirst(sm);
        } else {
          debug("SERIOUS ERROR: Request to record update for non-existant data socket to " + address);
        }
      }      
    } else { // type == TYPE_CONTROL
      synchronized (controlSockets) {
        SocketManager sm = (SocketManager)controlSockets.get(address);
        if (sm != null) {
          queue.remove(sm);
          queue.addFirst(sm);
        } else {
          debug("SERIOUS ERROR: Request to record update for non-existant control socket to " + address);
        }
      }
    }
  }
*/

  /**
   * Method which is designed to be called *ONCE THE SOCKET HAS BEEN CLOSED*.
   * This method simply updates the bookeeping, but does not actually close the
   * socket.
   *
   * @param address DESCRIBE THE PARAMETER
   * @param manager DESCRIBE THE PARAMETER
   */
  protected void socketClosed(SocketManager manager) {
      queue.remove(manager);
  }
  
  /**
   * Method which is designed to be called by node handles when they wish to
   * open a socket to their remote node. This method will determine if another
   * node handle needs to disconnect, and will disconnect the ejected node
   * handle if necessary.
   *
   * @param address DESCRIBE THE PARAMETER
   * @param manager DESCRIBE THE PARAMETER
   */
  protected void socketOpened(SocketManager manager) {
    synchronized (queue) {
      queue.addFirst(manager);
      closeSocketIfNecessary();
    }
  }

  protected void closeSocketIfNecessary() {
    if (queue.size() > scm.MAX_OPEN_SOCKETS) {
      SocketManager toClose = (SocketManager) queue.removeLast();
      debug("Too many sockets open - closing socket to " + toClose);
      toClose.close();
    }    
  }  
  
  private void debug(String s) {
    scm.debug(s);
  }

  
}
