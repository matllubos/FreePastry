
package rice.pastry.wire;

import java.util.*;

import rice.pastry.*;
import rice.pastry.dist.*;
import rice.pastry.messaging.*;
import rice.pastry.security.*;
import rice.pastry.standard.*;

/**
 * Security manager for wire connections between nodes.
 *
 * @author Alan Mislove
 */

public class WirePastrySecurityManager implements PastrySecurityManager {

  private PastryNode localnode;
  private WireNodeHandle localhandle;
  private WireNodeHandlePool pool;

  /**
   * Constructor.
   *
   * @param snh DESCRIBE THE PARAMETER
   * @param snhp DESCRIBE THE PARAMETER
   */
  public WirePastrySecurityManager(WireNodeHandle snh, WireNodeHandlePool snhp) {
    localhandle = snh;
    pool = snhp;
  }

  /**
   * Gets the current time for a timestamp.
   *
   * @return the timestamp.
   */

  public Date getTimestamp() {
    return new Date();
  }

  /**
   * Sets the local Pastry node after it is fully constructed.
   *
   * @param pn local Pastry node.
   */
  public void setLocalPastryNode(PastryNode pn) {
    localnode = pn;
  }

  /**
   * This method takes a message and returns true if the message is safe and
   * false otherwise.
   *
   * @param msg a message.
   * @return if the message is safe, false otherwise.
   */

  public boolean verifyMessage(Message msg) {
    return true;
  }

  /**
   * Checks to see if these credentials can be associated with the address.
   *
   * @param cred some credentials.
   * @param addr an address.
   * @return true if the credentials match the address, false otherwise.
   */

  public boolean verifyAddressBinding(Credentials cred, Address addr) {
    return true;
  }

  /**
   * Verify node handle safety.
   *
   * @param handle the handle to check.
   * @return the verified node handle
   */

  public NodeHandle verifyNodeHandle(NodeHandle handle) {
    return pool.coalesce((DistNodeHandle) handle);
  }
}
