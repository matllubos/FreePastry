package rice.post.messaging;

import java.io.*;

import rice.p2p.commonapi.*;
import rice.p2p.commonapi.rawserialization.*;

/**
 * This is a wrapper message for all Post messages which
 * are to be sent over the Pastry messaging system.
 */
public class PostPastryMessage implements RawMessage, SignedPostMessageWrapper {
  public static final short TYPE = 8;

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
   * Method which should return the priority level of this message.  The messages
   * can range in priority from 0 (highest priority) to Integer.MAX_VALUE (lowest) -
   * when sending messages across the wire, the queue is sorted by message priority.
   * If the queue reaches its limit, the lowest priority messages are discarded.  Thus,
   * applications which are very verbose should have LOW_PRIORITY or lower, and
   * applications which are somewhat quiet are allowed to have MEDIUM_PRIORITY or
   * possibly even HIGH_PRIORITY.
   *
   * @return This message's priority
   */
  public int getPriority() {
    return MEDIUM_HIGH_PRIORITY;
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

  public short getType() {
    return TYPE;
  }

  public PostPastryMessage(InputBuffer buf, Endpoint endpoint) throws IOException {
    message = new SignedPostMessage(buf, endpoint);
  }
  public void serialize(OutputBuffer buf) throws IOException {
    message.serialize(buf);
  }
  
}
