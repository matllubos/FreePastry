package rice.post.email;

import rice.pastry.*;
import rice.past.*;
import rice.post.*;

/**
 * Represents the idea of a POST email address. Constructor takes a string
 * (intended to be human-readable, perhaps similar in some way to an
 * Internet mail address). You can get the corresponding NodeId out of
 * this address.
 */

public class EmailGroupAddress extends PostGroupAddress {

  /**
   * Constructs an EmailGroupAddress with the given human-readable
   * string.
   *
   * @param name The name of the group
   */
  public EmailGroupAddress(String name) {
  }

  /**
   * Gets the human-readable string of this EmailAddress.
   *
   * @return The String representation of this email address
   */
  public String getName() {
    return null;
  }

  /**
   * Gets the NodeId corresponding to this EmailAddress.
   *
   * @return The hash of this group's name, which is the email address.
   */
  public NodeId getAddress() {
    return null;
  }

  /**
   * Returns a list of all of the email address contained in
   * this EmailGroupAddress.
   *
   * @param past The PAST service used to retrieve this email address
   * @return The list of all contained addresses
   */
  public PostEntityAddress[] getAddresses(PASTService past) {
    return null;
  }
}
