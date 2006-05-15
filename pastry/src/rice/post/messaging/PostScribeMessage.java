package rice.post.messaging;

import java.io.*;

import rice.p2p.commonapi.Endpoint;
import rice.p2p.commonapi.rawserialization.*;
import rice.p2p.scribe.*;
import rice.p2p.scribe.rawserialization.RawScribeContent;

/**
 * This is a wrapper message for all Post messages which
 * are to be sent over the Scribe messaging system.
 */
public class PostScribeMessage implements RawScribeContent, SignedPostMessageWrapper {
  public static final short TYPE = 9;

  private SignedPostMessage message;
  
  /**
   * Builds a PostScribeMessage given a PostMessage to contain.
   *
   * @param message The message to wrap.
   */
  public PostScribeMessage(SignedPostMessage message) {
    this.message = message;
  }

  /**
   * Returns the internal SignedPostMessage.
   *
   * @return The contained SignedPostMessage.
   */
  public SignedPostMessage getMessage() {
    return message;
  }

  /***************** Raw Serialization ***************************************/
  public short getType() {
    return TYPE;
  }
  
  public void serialize(OutputBuffer buf) throws IOException {
    message.serialize(buf);
  }
  
  public PostScribeMessage(InputBuffer buf, Endpoint endpoint) throws IOException {
    message = new SignedPostMessage(buf, endpoint);
  }

}
