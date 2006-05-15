
package rice.post.delivery;

import java.io.*;

import rice.*;
import rice.post.*;
import rice.post.messaging.*;
import rice.p2p.past.*;
import rice.p2p.commonapi.*;
import rice.p2p.commonapi.rawserialization.InputBuffer;

/**
 * The undeliverable marker stored in Past
 *
 * @version $Id$
 * @author Alan Mislove
 * @author Ansley Post
 * @author Peter Druschel
 */
public class Undeliverable extends Delivery {
  public static final short TYPE = 172;

  private static final long serialVersionUID = 4077957479096391613L;

  /**
   * Constructor which takes the wrapped message
   *
   * @param message The message to deliver
   */
  protected Undeliverable(SignedPostMessage message, Id id) {
    super(message, id);
  }
  
  
  public Undeliverable(InputBuffer buf, Endpoint endpoint) throws IOException {
    super(buf, endpoint); 
  }
  
  public short getType() {
    return TYPE;
  }
}





