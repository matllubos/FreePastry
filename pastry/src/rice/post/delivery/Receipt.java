
package rice.post.delivery;

import java.io.Serializable;

import rice.*;
import rice.post.*;
import rice.post.messaging.*;
import rice.p2p.past.*;
import rice.p2p.commonapi.*;


/**
 * The receipt stored in Past
 *
 * @version $Id$
 * @author Alan Mislove
 * @author Ansley Post
 * @author Peter Druschel
 */
public class Receipt extends Delivery {
  
  /**
   * The signature
   */
  protected byte[] signature;
  
  /**
  * Constructor which takes the wrapped message
   *
   * @param message The message to deliver
   */
  protected Receipt(SignedPostMessage message, IdFactory factory, byte[] signature) {
    super(message, factory);
    this.signature = signature;
  }
  
  /**
   * Returns the internal signature
   *
   * @return The wrapped signature
   */
  public byte[] getSignature() {
    return signature;
  }
}





