package rice.post;

import rice.pastry.*;
import rice.past.*;

/**
 * This class represents the abstract notion of the address
 * of an group of users in the Post system.  
 */
public abstract class PostGroupAddress extends PostEntityAddress {

  /**
   * Constructor
   */
  public PostGroupAddress() {
  }

  /**
   * Returns all of the contained addresses, using the PAST client
   * if necessary.
   *
   * @param past The PASTClient to use, if necessary
   * @return All of the addresses in this group.
   */
  public abstract PostEntityAddress[] getAddresses(PASTService past);
}
