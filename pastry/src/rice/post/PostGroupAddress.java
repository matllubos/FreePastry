package rice.post;

import rice.pastry.*;
import rice.p2p.past.*;

/**
 * This class represents the abstract notion of the address
 * of an group of users in the Post system.
 * 
 * @version $Id$
 */
public abstract class PostGroupAddress extends PostEntityAddress {

  /**
   * Constructor
   */
  public PostGroupAddress() {
  }

  /**
   * Returns all of the contained addresses, using the PAST service
   * if necessary.
   *
   * @param past The PAST service to use, if necessary
   * @return All of the addresses in this group.
   */
  public abstract PostEntityAddress[] getAddresses(Past past);
}
