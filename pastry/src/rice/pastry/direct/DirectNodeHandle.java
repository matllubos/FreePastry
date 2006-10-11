
package rice.pastry.direct;

import java.io.IOException;
import java.util.*;

import rice.environment.logging.Logger;
import rice.p2p.commonapi.rawserialization.OutputBuffer;
import rice.pastry.*;
import rice.pastry.messaging.*;

/**
 * the node handle used with the direct network
 *
 * @version $Id$
 * @author Andrew Ladd
 * @author Rongmei Zhang/Y. Charlie Hu
 */

public class DirectNodeHandle extends NodeHandle implements Observer {
  private DirectPastryNode remoteNode;
  public NetworkSimulator simulator;
  protected Logger logger;
  /**
   * Constructor for DirectNodeHandle.
   *
   * @param ln The local pastry node
   * @param rn The remote pastry node
   * @param sim The current network simulator
   */
  DirectNodeHandle(DirectPastryNode ln, DirectPastryNode rn, NetworkSimulator sim) {
    localnode = ln;
    logger = ln.getEnvironment().getLogManager().getLogger(getClass(), null);
    if (rn == null) throw new IllegalArgumentException("rn must be non-null");
    remoteNode = rn;
    simulator = sim;
    
    rn.addObserver(this);
  }

  /**
   * Gets the Remote attribute of the DirectNodeHandle object
   *
   * @return The Remote value
   */
  public DirectPastryNode getRemote() {
    return remoteNode;
  }

  /**
   * Gets the NodeId attribute of the DirectNodeHandle object
   *
   * @return The NodeId value
   */
  public Id getNodeId() {
    return remoteNode.getNodeId();
  }

  /**
   * Gets the Alive attribute of the DirectNodeHandle object
   *
   * @return The Alive value
   */
  public int getLiveness() {
    if (remoteNode.isAlive()) {
      return LIVENESS_ALIVE;
    }
    return LIVENESS_DEAD; 
  }

  /**
   * Gets the Simulator attribute of the DirectNodeHandle object
   *
   * @return The Simulator value
   */
  public NetworkSimulator getSimulator() {
    return simulator;
  }

  /**
   * DESCRIBE THE METHOD
   *
   * @param arg DESCRIBE THE PARAMETER
   */
  public void notifyObservers(Object arg) {
    setChanged();
    super.notifyObservers(arg);
  }

  /**
   * DESCRIBE THE METHOD
   *
   * @return DESCRIBE THE RETURN VALUE
   */
  public boolean ping() {
    return isAlive();
  }
  
  public final void assertLocalNode() {
    if (DirectPastryNode.getCurrentNode() == null) {
//      ctor.printStackTrace();
      throw new RuntimeException("PANIC: localnode is null in " + this+"@"+System.identityHashCode(this));
    }
  }

  /**
   * DESCRIBE THE METHOD
   *
   * @return DESCRIBE THE RETURN VALUE
   */
  public int proximity() {
    assertLocalNode();
    return getLocalNode().proximity(this);
  }

  /**
   * DESCRIBE THE METHOD
   *
   * @param msg DESCRIBE THE PARAMETER
   */
  public void receiveMessage(Message msg) {
    DirectPastryNode.getCurrentNode().send(this, msg);
  }

  /**
   * Equivalence relation for nodehandles. They are equal if and only if their corresponding NodeIds
   * are equal.
   *
   * @param obj the other nodehandle .
   * @return true if they are equal, false otherwise.
   */
  public boolean equals(Object obj) {
    // we know that there is only one of these per node in the simulator
    return obj == this;
    
//    if (obj == null) {
//      return false;
//    }
//    DirectNodeHandle nh = (DirectNodeHandle) obj;
//
//    if (this.remoteNode.getNodeId().equals(nh.remoteNode.getNodeId())) {
//      return true;
//    } else {
//      return false;
//    }
  }

  /**
   * Hash codes for node handles.It is the hashcode of their corresponding NodeId's.
   *
   * @return a hash code.
   */
  public int hashCode() {
    return this.getNodeId().hashCode();
  }

  /**
   * DESCRIBE THE METHOD
   *
   * @return DESCRIBE THE RETURN VALUE
   */
  public String toString() {
    return "[DNH " + getNodeId() + "]";
  }

  /**
   * Only notify if dead.  Note that this is limitied in that it's not possible 
   * to simulate a byzantine failure of a node.  But that's out of the scope of 
   * the simulator.  If we leave in the first arg, the node notifies DECLARED_LIVE 
   * way too often.
   */
  public void update(Observable arg0, Object arg1) {
    if (remoteNode.alive) {
//      notifyObservers(NodeHandle.DECLARED_LIVE);      
    } else {
//      System.out.println(this+"Notifying dead");
      notifyObservers(NodeHandle.DECLARED_DEAD);      
    }
  }

  public void serialize(OutputBuffer buf) throws IOException {
    throw new RuntimeException("Should not be called.");
  }
}
