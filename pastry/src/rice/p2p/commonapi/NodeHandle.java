
package rice.p2p.commonapi;

import java.io.*;
import java.util.*;

/**
 * @(#) NodeHandle.java
 *
 * This class is an abstraction of a node handle from the CommonAPI paper. A
 * node handle is a handle to a known node, which conceptually includes the
 * node's Id, as well as the node's underlying network address (such as IP/port).
 *
 * This class is (unfortunately) an abstact class due to the need to be observable.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 * @author Peter Druschel
 */
public abstract class NodeHandle extends Observable implements Serializable  {

  // constants defining types of observable events
  public static final Integer PROXIMITY_CHANGED = new Integer(1);
  public static final Integer DECLARED_DEAD = new Integer(2);
  public static final Integer DECLARED_LIVE = new Integer(3);
  
  // serialver
  private static final long serialVersionUID = 4761193998848368227L;
  
  /**
   * Returns this node's id.
   *
   * @return The corresponding node's id.
   */
  public abstract Id getId();

  /**
   * Returns whether or not this node is currently alive
   *
   * @return Whether or not this node is currently alive
   */
  public abstract boolean isAlive();

  /**
   * Returns the current proximity value of this node
   *
   * @return The current proximity value of this node
   */
  public abstract int proximity();
  
  /**
   * Requests that the underlying transport layer check to ensure
   * that the remote node is live.  If the node is found to be live, nothing
   * happens, but if the node does not respond, the transport layer
   * make take steps to verfify that the node is dead.  Such steps
   * could include finding an alteranate route to the node.
   *
   * @return Whether or not the node is currently alive
   */
  public abstract boolean checkLiveness();
  
}


