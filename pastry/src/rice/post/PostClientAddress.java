package rice.post;

import rice.pastry.*;

/**
 * This class represents the notion of an address, which
 * uniquely identifies an application running on top of the
 * POST service. This class is designed using the factory
 * pattery, with the getAddress() method as the entrance
 * into the factory.
 * 
 * @version $Id$
 */
public final class PostClientAddress {

  /**
   * Constructor
   */
  private PostClientAddress() {
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
    // NEEDS TO BE IMPLEMENTED
    return null;
  }

}