package rice.post;

import rice.pastry.*;

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
}
