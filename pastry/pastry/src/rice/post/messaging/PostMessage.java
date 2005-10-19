package rice.post.messaging;

import java.io.*;

import rice.post.*;

/**
 * This is abstraction of all messages in the Post system.  
 */
public abstract class PostMessage implements Serializable {

  // the sender of this PostMessage
  private PostEntityAddress sender;

  /**
   * Constructs a PostMessage given the name of the
   * sender.
   *
   * @param sender The sender of this message.
   */
  public PostMessage(PostEntityAddress sender) {
    if (sender == null) {
      throw new IllegalArgumentException("Attempt to build PostMessage with null sender!");
    }
    
    this.sender = sender;
  }

  /**
   * Returns the sender of this message.
   *
   * @return The sender
   */
  public final PostEntityAddress getSender() {
    return sender;
  }
}
