package rice.post;

import rice.p2p.commonapi.*;
import rice.p2p.past.*;

/**
 * This class represents the abstract notion of the address
 * of an group of users in the Post system.
 * 
 * @version $Id$
 */
public class PostGroupAddress extends PostEntityAddress {

  private Id id;
  
  /**
   * Constructor
   */
  public PostGroupAddress(IdFactory factory, String name) {
    id = getId(factory, name);
  }

  /**
    * Returns the address associated with this useraddress
   *
   * @return The corresponding address
   */
  public Id getAddress() {
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
