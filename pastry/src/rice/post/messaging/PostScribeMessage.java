package rice.post.messaging;

import java.io.*;
import rice.p2p.scribe.*;

/**
 * This is a wrapper message for all Post messages which
 * are to be sent over the Scribe messaging system.
 */
public class PostScribeMessage implements ScribeContent, SignedPostMessageWrapper {

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

}
