package rice.post.messaging;

import java.io.*;

import rice.pastry.messaging.*;

/**
 * This is a wrapper message for all Post messages which
 * are to be sent over the Pastry messaging system.
 */
public class PostPastryMessage extends Message implements PostMessageWrapper {

  private PostMessage message;
  
  /**
   * Builds a PostPastryMessage given a PostMessage.
   *
   * @param message The internal message.
   */
  public PostPastryMessage(PostMessage message) {
    super(PostAddress.instance());

    this.message = message;
  }

  /**
   * Returns the internal PostMessage.
   *
   * @return The contained PostMessage.
   */
  public PostMessage getMessage() {
    return message;
  }
  
}
