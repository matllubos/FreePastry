package rice.pastry.socket;

import java.lang.ref.*;
import java.net.*;
import java.util.*;

import rice.environment.logging.Logger;
import rice.pastry.dist.*;

/**
 * The DistNodeHandlePool controls all of the node handles in use by the
 * DistPastryNode. It ensures that there is only one "active" node handle for
 * each remote pastry node.  
 *
 * @version $Id: SocketNodeHandlePool.java,v 1.4 2003/12/22 03:24:47 amislove
 *      Exp $
 * @author Alan Mislove
 */
public class SocketNodeHandlePool extends DistNodeHandlePool {

  /**
   * The node which this pool serves
   */
  protected SocketPastryNode node;

  /**
   * A mapping containing references to all of the handles in the system
   */
  protected Hashtable handles;

  /**
   * Constructor.
   *
   * @param node DESCRIBE THE PARAMETER
   */
  public SocketNodeHandlePool(SocketPastryNode node) {
    this.node = node;
    handles = new Hashtable();
  }

  /**
   * The method verifies a DistNodeHandle. If a node handle to the pastry node
   * has never been seen before, an entry is added, and this node handle is
   * referred to in the future. Otherwise, this method returns the previously
   * verified node handle to the pastry node.
   *
   * @param handle The node handle to verify.
   * @return The node handle to use to talk to the pastry node.
   */
  public DistNodeHandle coalesce(DistNodeHandle handle) {
    return handle;
  }

  /**
   * This method should be called by a newly constructed node handle, or by a
   * handle which has just arrived at a new node. It records the handle,
   * allowing observers on the handle to be properly updated.
   *
   * @param handle The newly created/deserialized handle
   */
  protected void record(SocketNodeHandle handle) {
    Vector vector = (Vector) handles.get(handle.getEpochAddress());

    if (vector == null) {
      vector = new Vector();
      handles.put(handle.getEpochAddress(), vector);
    }

    vector.addElement(new WeakReference(handle));
  }

  /**
   * This method updates all of the handles to the given address with the
   * specified update.
   *
   * @param update The update to notify the handles of
   * @param address DESCRIBE THE PARAMETER
   */
  protected void update(EpochInetSocketAddress address, Object update) {
    Vector vector = (Vector) handles.get(address);

    if (vector != null) {
      boolean printed = false;
      Object[] array = vector.toArray();

      for (int i = 0; i < array.length; i++) {
        SocketNodeHandle handle = (SocketNodeHandle) ((WeakReference) array[i]).get();

        if (handle != null) {
          if (!printed) {
            if (update == SocketNodeHandle.DECLARED_DEAD) {
              node.getEnvironment().getLogManager().getLogger(SocketNodeHandle.class, null).log(Logger.FINE,
                  "found " + handle.getNodeId() + " to be dead.");
            } else if (update == SocketNodeHandle.DECLARED_LIVE) {
              node.getEnvironment().getLogManager().getLogger(SocketNodeHandle.class, null).log(Logger.FINE,
                  "found " + handle.getNodeId() + " to be alive again.");
            }

            printed = true;
          }

          handle.update(update);
        } else {
          vector.remove(array[i]);
        }
      }
    }
  }
}
