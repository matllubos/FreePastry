
package rice.pastry.direct;

import rice.pastry.*;
import rice.pastry.messaging.*;
import rice.pastry.security.*;
import rice.pastry.standard.*;

import java.util.*;

/**
 * Security manager for direct connections between nodes.
 *
 * @version $Id$
 *
 * @author Andrew Ladd
 */
public class DirectSecurityManager implements PastrySecurityManager {
  
  private PastryNode pnode;
  private NetworkSimulator sim;
  private Hashtable pool;

  /**
   * Constructor.
   */

  public DirectSecurityManager(NetworkSimulator ns) {
    pnode = null;
    sim = ns;
    pool = new Hashtable();
  }

  /**
   * Sets the local pastry node.
   *
   * @param pn pastry node.
   */
  public void setLocalPastryNode(PastryNode local) { pnode = local; }

  /**
   * This method takes a message and returns true
   * if the message is safe and false otherwise.
   *
   * @param msg a message.
   * @return if the message is safe, false otherwise.
   */
  public boolean verifyMessage(Message msg) { return true; }

  /**
   * Checks to see if these credentials can be associated with the address.
   *
   * @param cred some credentials.
   * @param addr an address.
   *
   * @return true if the credentials match the address, false otherwise.
   */
  public boolean verifyAddressBinding(Credentials cred, Address addr) { return true; }

  /**
   * Verify node handle safety.
   *
   * @param handle the handle to check.
   *
   * @return the verified node handle
   */
  public NodeHandle verifyNodeHandle(NodeHandle handle) {
    return handle;
    
//    NodeId local = pnode.getNodeId();
//    NodeId nid = handle.getNodeId();
//
//    if (local.equals(nid)) {
//      return pnode.getLocalHandle();
//    } else if (handle instanceof DirectNodeHandle) {
//      DirectNodeHandle dnh = (DirectNodeHandle) handle;
//
//      DirectNodeHandle retDnh = (DirectNodeHandle) pool.get(handle.getNodeId());
//
//      if (retDnh == null) {
//        retDnh = new DirectNodeHandle(pnode, dnh.getRemote(), sim);
//        pool.put(handle.getNodeId(), retDnh);
//
//        sim.registerNodeId(retDnh);
//      }
//
//      return retDnh;
//    } else throw new Error("node handle of unknown type");
  }

  /**
   * Gets the current time for a timestamp.
   *
   * @return the timestamp.
   */
  public Date getTimestamp() { return new Date(); }
}
