package rice.post.messaging;

import java.io.*;

import rice.pastry.messaging.*;

/**
 * This is a wrapper message for all Post messages which
 * are to be sent over the Pastry messaging system.
 */
public class PostPastryMessage extends Message implements SignedPostMessageWrapper {

  private SignedPostMessage message;
  
  /**
   * Builds a PostPastryMessage given a PostMessage.
   *
   * @param message The internal message.
   */
  public PostPastryMessage(SignedPostMessage message) {
    super(PostAddress.instance());

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
