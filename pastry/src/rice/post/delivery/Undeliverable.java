
package rice.post.delivery;

import java.io.Serializable;

import rice.*;
import rice.post.*;
import rice.post.messaging.*;
import rice.p2p.past.*;
import rice.p2p.commonapi.*;

/**
 * The undeliverable marker stored in Past
 *
 * @version $Id$
 * @author Alan Mislove
 * @author Ansley Post
 * @author Peter Druschel
 */
public class Undeliverable extends Delivery {
  
  /**
   * Constructor which takes the wrapped message
   *
   * @param message The message to deliver
   */
  protected Undeliverable(SignedPostMessage message, IdFactory factory) {
    super(message, factory);
  }
}





