
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
  
  private static final long serialVersionUID = -2762703066657973942L;

  /**
   * The signature
   */
  protected byte[] signature;
  
  /**
  * Constructor which takes the wrapped message
   *
   * @param message The message to deliver
   */
  protected Receipt(SignedPostMessage message, Id id, byte[] signature) {
    super(message, id);
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





