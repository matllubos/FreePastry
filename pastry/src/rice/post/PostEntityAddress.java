package rice.post;

import java.security.*;

import rice.pastry.*;

/**
 * This class represents the abstract notion of the address
 * of an identity in the Post system.  This class is designed
 * to be extended have address for both Post users and groups
 * of users.
 * 
 * @version $Id$
 */
public abstract class PostEntityAddress {

  /**
   * Constructor
   */
  public PostEntityAddress() {
  }
    
  /**
   * @return The NodeId which this address maps to.
   */
  public abstract NodeId getAddress();

  /**
   * Utility method for creating the nodeId associated with a
   * specific string.
   *
   * @param string The string
   * @returns The corresponding nodeId.
   */
  protected static NodeId getNodeId(String string) {
    MessageDigest md = null;

    try {
      md = MessageDigest.getInstance("SHA");
    } catch (NoSuchAlgorithmException e) {
      System.err.println("No SHA support!");
    }

    md.update(string.getBytes());
    return new NodeId(md.digest());
  }

}