package rice.post.messaging;

import java.io.*;

import rice.p2p.commonapi.*;
import rice.p2p.commonapi.rawserialization.*;

/**
 * This is a message reminding POST to backup its log heads
 */
public class BackupMessage implements RawMessage {
  
  public static final short TYPE = 1;
  
  public byte getPriority() {
    return MEDIUM_PRIORITY;
  }

  public short getType() {
    return TYPE;
  }

  public void serialize(OutputBuffer buf) throws IOException {
    // empty
  }
  
}
