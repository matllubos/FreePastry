package rice.post;

import java.security.*;
import java.io.*;

import rice.pastry.*;
import rice.pastry.multiring.*;

/**
 * This class represents the abstract notion of the address
 * of an identity in the Post system.  This class is designed
 * to be extended have address for both Post users and groups
 * of users.
 * 
 * @version $Id$
 */
public abstract class PostEntityAddress implements Serializable {

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

    if (string.indexOf("@") == -1) {
      md.update(string.getBytes());
      NodeId userNodeId = new NodeId(md.digest());
      return new RingNodeId(userNodeId, MultiRingPastryNode.GLOBAL_RING_ID);
    } else {
      String user = string.substring(0, string.indexOf("@"));
      String domain = string.substring(string.indexOf("@") + 1);
      
      md.update(user.getBytes());
      NodeId userNodeId = new NodeId(md.digest());

      md.update(domain.getBytes());
      RingId domainRingId = new RingId(md.digest());
      return new RingNodeId(userNodeId, domainRingId);
    }
  }
}