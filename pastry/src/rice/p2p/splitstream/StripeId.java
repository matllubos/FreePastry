package rice.p2p.splitstream;

import rice.p2p.commonapi.*;

/**
 * This class wraps the nodeId object so we can use type checking and allow more readable and
 * understandable code. All it does is subclass the nodeId and provide a constructor that allows the
 * wrapping of a NodeId object to create a concrete subclass
 *
 * @version $Id$
 * @author Ansley Post
 */
public class StripeId {

  /**
   * DESCRIBE THE FIELD
   */
  protected Id id;

  /**
   * Constructor that takes in a nodeId and makes a StripeId
   *
   * @param id DESCRIBE THE PARAMETER
   */
  public StripeId(Id id) {
    this.id = id;
  }

  /**
   * Gets the Id attribute of the StripeId object
   *
   * @return The Id value
   */
  public Id getId() {
    return id;
  }
}
