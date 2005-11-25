
package rice.pastry;

import rice.environment.logging.Logger;
import rice.pastry.messaging.*;

import java.io.*;
import java.util.*;

/**
 * Interface for handles to remote nodes.
 *
 * @version $Id$
 *
 * @author Andrew Ladd
 */
public abstract class NodeHandle extends rice.p2p.commonapi.NodeHandle implements MessageReceiver 
{

  public static final int LIVENESS_ALIVE = 1;
  public static final int LIVENESS_SUSPECTED = 2;
  public static final int LIVENESS_DEAD = 3;
    
  // the local pastry node
  protected transient PastryNode localnode;
  protected transient Logger logger;
  
  static final long serialVersionUID = 987479397660721015L;
  /**
   * Gets the nodeId of this Pastry node.
   *
   * @return the node id.
   */
  public abstract NodeId getNodeId();

  public rice.p2p.commonapi.Id getId() {
    return getNodeId();
  }

  /**
   * Returns the last known liveness information about the Pastry node associated with this handle.
   * Invoking this method does not cause network activity.
   *
   * @return true if the node is alive, false otherwise.
   */
  public final boolean isAlive() {
    return getLiveness() < LIVENESS_DEAD; 
  }

  /**
   * A more detailed version of isAlive().  This can return 3 states:
   * 
   * @return LIVENESS_ALIVE, LIVENESS_SUSPECTED, LIVENESS_DEAD
   */
  public abstract int getLiveness();
  
  /**
   * Method which FORCES a check of liveness of the remote node.  Note that
   * this method should ONLY be called by internal Pastry maintenance algorithms - 
   * this is NOT to be used by applications.  Doing so will likely cause a
   * blowup of liveness traffic.
   *
   * @return true if node is currently alive.
   */
  public boolean checkLiveness() {
    return ping();
  }

  /**
   * Returns the last known proximity information about the Pastry node associated with this handle.
   * Invoking this method does not cause network activity.
   *
   * Smaller values imply greater proximity. The exact nature and interpretation of the proximity metric
   * implementation-specific.
   *
   * @return the proximity metric value
   */
  public abstract int proximity();

  /**
   * Ping the node. Refreshes the cached liveness status and proximity value of the Pastry node associated
   * with this.
   * Invoking this method causes network activity.
   *
   * @return true if node is currently alive.
   */
  public abstract boolean ping();

  /**
   * Accessor method.
   */
  public final PastryNode getLocalNode() {
    return localnode;
  }

//  transient Exception ctor;
//  public NodeHandle() {
//    ctor = new Exception("ctor"); 
//  }
  
  /**
   * May be called from handle etc methods to ensure that local node has
   * been set, either on construction or on deserialization/receivemsg.
   */
  public final void assertLocalNode() {
    if (localnode == null) {
//      ctor.printStackTrace();
      throw new RuntimeException("PANIC: localnode is null in " + this+"@"+System.identityHashCode(this));
    }
  }

  /**
   * Equality operator for nodehandles.
   *
   * @param obj a nodehandle object
   * @return true if they are equal, false otherwise.
   */
  public abstract boolean equals(Object obj);

  /**
   * Method which is used by Pastry to start the bootstrapping process on the 
   * local node using this handle as the bootstrap handle.  Default behavior is
   * simply to call receiveMessage(msg), but transport layer implementations may
   * care to perform other tasks by overriding this method, since the node is
   * not technically part of the ring yet.
   *
   * @param msg the bootstrap message.
   */
  public void bootstrap(Message msg) {
    receiveMessage(msg);
  }

  /**
   * Hash codes for nodehandles.
   *
   * @return a hash code.
   */
  public abstract int hashCode();

}



