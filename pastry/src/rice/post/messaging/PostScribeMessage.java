package rice.post.messaging;

import java.io.*;

/**
 * This is a wrapper message for all Post messages which
 * are to be sent over the Scribe messaging system.
 */
public class PostScribeMessage implements PostMessageWrapper {

  /**
   * Builds a PostScribeMessage given a PostMessage to contain.
   *
   * @param message The message to wrap.
   */
  public PostScribeMessage(PostMessage message) {
  }

  /**
   * Returns the internal PostMessage.
   *
   * @return The contained PostMessage.
   */
  public PostMessage getMessage() {
    return null;
  }

}
