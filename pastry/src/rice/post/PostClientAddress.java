package rice.post;

import rice.pastry.*;

/**
 * This class represents the notion of an address, which
 * uniquely identifies an application running on top of the
 * POST service.  Each application should provide at least on
 * implementation of this class. All of the implementations should
 * implement this class as a singleton, so that the .equals method
 * returns true for all other instances of this class.
 */
public abstract class PostClientAddress {

  /**
   * Constructor
   */
  public PostClientAddress() {
  }

}