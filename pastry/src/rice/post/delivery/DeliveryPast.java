
package rice.post.delivery;

import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.past.*;
import rice.p2p.past.gc.*;
import rice.post.*;

/**
 * Interface which represents a POST-specific pending-delivery PAST storage.
 *
 * @version $Id$
 * @author Alan Mislove
 * @author Ansley Post
 * @author Peter Druschel
 */
public interface DeliveryPast extends GCPast {
  
  /**
   * Method which periodically checks to see if we've got receipts for
   * any outstanding messages.  If so, then we remove the outstanding message
   * from our pending list.
   */
  public void synchronize(Continuation command);
  
  /**
   * Returns the list of PostEntityaddress for which we are the primary replica
   * responsible for delivering messages.
   *
   * @param command The command to return the results to
   */
  public void getGroups(Continuation command);
  
  /**
   * Returns the first message which is still pending to the given address.  If no
   * such message exists, null is returned
   *
   * @param address The address for the message
   * @param command The command to return the results to
   */
  public void getMessage(PostEntityAddress address, Continuation command);
  
}
