
package rice.pastry;

import java.io.*;
import java.util.*;

import rice.pastry.messaging.*;
import rice.pastry.rmi.*;

/**
 * Implementation of the LocalNodeI interface that some Serializable classes (such
 * as Certificate) extend, if they want to be kept informed of what
 * node they're on. If a class cannot use this provided implementation (for reasons
 * such as multiple inheritance), it should implement the method provided in the
 * LocalNode interface in the same manner as these.
 *
 * @version $Id$
 *
 * @author Sitaram Iyer
 * @author Alan Mislove
 */
public abstract class LocalNode implements LocalNodeI {

  // the local pastry node
  private transient PastryNode localnode;

  public LocalNode() { localnode = null; }

  /**
   * Accessor method.
   */
  public final PastryNode getLocalNode() { return localnode; }

  /**
   * Accessor method. Notifies the overridable afterSetLocalNode.
   */
  public final void setLocalNode(PastryNode pn) {
    localnode = pn;
    if (localnode != null) afterSetLocalNode();
  }

  /**
   * Method that can be overridden by handle to set isLocal, etc.
   */
  public void afterSetLocalNode() {}

  /**
   * May be called from handle etc methods to ensure that local node has
   * been set, either on construction or on deserialization/receivemsg.
   */
  public final void assertLocalNode() {
    if (localnode == null) {
      System.out.println("PANIC: localnode is null in " + this);
      (new Exception()).printStackTrace();
    }
  }

  /**
   * Called on deserialization. Adds itself to a pending-setLocalNode
   * list. This list is in a static (global) hash, indexed by the
   * ObjectInputStream. Refer to README.handles_localnode for details.
   */
  private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
    in.defaultReadObject();
    
    if (! (in instanceof PastryObjectInputStream)) {
      LocalNodeI.pending.addPending(in, this);
    }
  }
}
