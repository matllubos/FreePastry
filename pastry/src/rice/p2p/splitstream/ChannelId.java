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
public class ChannelId {

  /**
   * DESCRIBE THE FIELD
   */
  protected Id id;

  /**
   * Constructor that takes in an Id and makes a ChannelId
   *
   * @param id DESCRIBE THE PARAMETER
   */
  public ChannelId(Id id) {
    this.id = id;
  }

  /**
   * Constructor that takes in a String and makes a ChannelId
   *
   * @param name DESCRIBE THE PARAMETER
   */
  public ChannelId(String name) {
    this.id = null;
  }

  /**
   * Gets the Id attribute of the ChannelId object
   *
   * @return The Id value
   */
  public Id getId() {
    return id;
  }
}
