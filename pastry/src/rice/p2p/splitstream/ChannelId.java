package rice.p2p.splitstream;

import rice.p2p.commonapi.*;

/**
 * This class wraps the nodeId object so we can use type checking and allow more readable and
 * understandable code. All it does is subclass the nodeId and provide a constructor that allows the
 * wrapping of a NodeId object to create a concrete subclass
 *
 * @version $Id$
 * @author Ansley Post
 * @author Alan Mislove
 */
public class ChannelId {

  /**
   * The underlying id for this channelid
   */
  protected Id id;

  /**
   * Constructor that takes in an Id and makes a ChannelId
   *
   * @param id The underlying id for this channelid
   */
  public ChannelId(Id id) {
    this.id = id;
  }

  /**
   * Constructor that takes in a String and makes a ChannelId
   *
   * @param name The name to create this channelId from
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

  public String toString() {
    return "[ChannelId " + id + "]";
  }

  public int hashCode() {
    return id.hashCode();
  }

  public boolean equals(Object o) {
    if (! (o instanceof ChannelId)) {
      return false;
    }

    return ((ChannelId) o).id.equals(id);
  }
}
