package rice.post.messaging;

import java.io.*;

import rice.p2p.commonapi.*;

/**
 * This is a wrapper message for all Post messages which
 * are to be sent over the Pastry messaging system.
 */
public class PostPastryMessage implements Message, SignedPostMessageWrapper {

  private SignedPostMessage message;
  
  static final long serialVersionUID = 8591215136628275133L;
  
  /**
   * Builds a PostPastryMessage given a PostMessage.
   *
   * @param message The internal message.
   */
  public PostPastryMessage(SignedPostMessage message) {
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
  
  public String toString() {
    return "[PPM " + message + "]"; 
  }
  
}
