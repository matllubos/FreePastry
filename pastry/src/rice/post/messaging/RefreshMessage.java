package rice.post.messaging;

import java.io.*;

import rice.p2p.commonapi.*;

/**
 * This is a message  reminding POST to refresh all data items stored
 * in GCPast
 */
public class RefreshMessage implements Message {
  public static final short TYPE = 12;

  public byte getPriority() {
    return MEDIUM_PRIORITY;
  }
  
}
