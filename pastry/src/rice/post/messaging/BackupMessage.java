package rice.post.messaging;

import java.io.*;

import rice.p2p.commonapi.*;

/**
 * This is a message reminding POST to backup its log heads
 */
public class BackupMessage implements Message {
  
  public int getPriority() {
    return MEDIUM_PRIORITY;
  }
  
}
