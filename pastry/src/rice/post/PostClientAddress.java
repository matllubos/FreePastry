package rice.post;

import rice.pastry.*;

import java.io.*;

/**
 * This class represents the notion of an address, which
 * uniquely identifies an application running on top of the
 * POST service. This class is designed using the factory
 * pattery, with the getAddress() method as the entrance
 * into the factory.
 * 
 * @version $Id$
 */
public final class PostClientAddress implements Serializable {

  private static final long serialVersionUID = 7140777125939291819L;
  
  protected String name;
  
  /**
   * Constructor
   */
  public PostClientAddress(String name) {
    this.name = name;
  }

  public boolean equals(Object o) {
    if (o instanceof PostClientAddress) {
      return ((PostClientAddress) o).name.equals(name);
    } else {
      return false;
    }
  }

  /**
   * Method by which one can generate a PostClientAddress.  This
   * method will always return the same address given the same
   * PostClient class.
   *
   * @param client The client wanting an address
   * @return A unique address for this class of client
   */
  public static PostClientAddress getAddress(PostClient client) {
    return new PostClientAddress(client.getClass().getName());
  }

  public int hashCode() {
    return name.hashCode();
  }

  public String toString() {
    return "PostClientAddress[" + name + "]";
  }
}