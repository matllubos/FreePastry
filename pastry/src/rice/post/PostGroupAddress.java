package rice.post;

import rice.pastry.*;
import rice.p2p.past.*;

/**
 * This class represents the abstract notion of the address
 * of an group of users in the Post system.
 * 
 * @version $Id$
 */
public class PostGroupAddress extends PostEntityAddress {

  private NodeId id;
  
  /**
   * Constructor
   */
  public PostGroupAddress(String name) {
    id = getNodeId(name);
  }

  /**
    * Returns the address associated with this useraddress
   *
   * @return The corresponding address
   */
  public NodeId getAddress() {
    return id;
  }

  public boolean equals(Object o) {
    if (o instanceof PostGroupAddress) {
      PostGroupAddress ua = (PostGroupAddress) o;
      return ua.getAddress().equals(id);
    }

    return false;
  }

  public int hashCode() {
    return id.hashCode();
  }
}
