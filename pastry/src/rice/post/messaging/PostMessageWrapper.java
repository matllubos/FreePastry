package rice.post.messaging;

import java.io.*;

/**
 * This class represents the abstraction of a class which
 * contains an internal PostMessage.  Thus, all messages which
 * are transmitted in Post are PostWrappers, each with a different
 * transport mechanism.
 */
public interface PostMessageWrapper extends Serializable {

  /**
   * Returns the internal PostMessage.
   *
   * @return The contained PostMessage.
   */
  public PostMessage getMessage();
  
}
