package rice.post.messaging;

import java.io.*;

/**
 * This class represents the abstraction of a class which
 * contains an internal PostMessage.  Thus, all messages which
 * are transmitted in Post are PostWrappers, each with a different
 * transport mechanism.
 */
public interface SignedPostMessageWrapper extends Serializable {

  /**
   * Returns the internal SignedPostMessage.
   *
   * @return The contained SignedPostMessage.
   */
  public SignedPostMessage getMessage();
  
}
