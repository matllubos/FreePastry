
package rice.pastry;

import java.io.*;
import java.util.*;

/**
 * Class which stores a list of LocalNodes waiting to have their
 * local pastry node to be set non-null.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */
public class PendingLocalNodesList {

  // maps ObjectInputStream -> LinkedList (of LocalNodes)
  private HashMap map = new HashMap();

  /**
   * Adds a pending LocalNode to the list of pending LocalNodes.
   *
   * @param in The input stream reading the LocalNode
   * @param client The LocalNodeI itself
   */
  public synchronized void addPending(ObjectInputStream in, LocalNodeI client) {
    LinkedList pendinglist = (LinkedList) map.get(in);
      
    if (pendinglist == null)
      map.put(in, pendinglist = new LinkedList());

    if (Log.ifp(8))
      System.out.println("pending " + this + " on list " + in);

    pendinglist.add(client);
  }

  /**
   * Sets all of the pending local nodes read in by the given input
   * stream.
   *
   * @param in The input stream reading the LocalNodes
   * @param node The local pastry node.
   */
  public void setPending(ObjectInputStream in, PastryNode node) {
    LinkedList pending = null;
    
    synchronized (this) {
      pending = (LinkedList) map.remove(in);
    }
      
    if (pending != null) {
      Iterator iter = pending.iterator();
      while (iter.hasNext()) {
        LocalNodeI ln = (LocalNodeI) iter.next();

        if (Log.ifp(8))
          System.out.println("setting local node " + node + " to " + ln);

        if (ln.getLocalNode() != null)
          System.out.println("setting local node twice! " + node + " to " + ln);

        ln.setLocalNode(node);
      }
    }
  }
}
