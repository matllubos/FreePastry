package rice.post;

import rice.pastry.*;
import rice.pastry.multiring.*;

/**
 * This class represents the abstract notion of the address
 * of an user in the Post system.
 * 
 * @version $Id$
 */
public class PostUserAddress extends PostEntityAddress {

  // the name of this user
  private String name;
  
  // the address of this user
  private NodeId address;
  
  /**
   * Constructor
   */
  public PostUserAddress(String name) {
    this.name = name;
    address = getNodeId(name);
  }

  /**
   * Constructor
   */
  public PostUserAddress(String name, RingId ringId) {
    this.name = name;
    address = getNodeId(name, ringId);
  }
  
  /**
   * Returns the address associated with this useraddress
   *
   * @return The corresponding address
   */
  public NodeId getAddress() {
    return address;
  }

  /**
   * Returns the name of this user
   *
   * @return The corresponding name
   */
  public String getName() {
    return name;
  }

  public String toString() {
    return name;
  }

  public boolean equals(Object o) {
    if (o instanceof PostUserAddress) {
      PostUserAddress ua = (PostUserAddress) o;
      return ua.getName().equals(name);
    }

    return false;
  }

  public int hashCode() {
    return name.hashCode();
  }
}
