package rice.post.email.messaging;

import rice.post.*;

/**
 * This class represents the address of the EmailService running
 * on top of Post.  This class is a Singleton.
 */
public class EmailAddress extends PostClientAddress {

  /**
   * Constructor
   */
  private EmailAddress() {
  }

  /**
   * Returns the instance of this address.
   *
   * @return The single instance of this address.
   */
  public static EmailAddress instance() {
    return null;
  }
  
  /**
   * Overridden equals in order to support equality.
   *
   * @return Whether or not the two object are of the same class.
   */
  public boolean equals(Object o) {
    return true;
  }
}