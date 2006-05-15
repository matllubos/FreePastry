package rice.post.messaging;

import java.io.*;

import rice.p2p.commonapi.*;
import rice.p2p.commonapi.rawserialization.*;

/**
 * This is a message  reminding POST to synchroize it's delivery message
 * requirements
 */
public class SynchronizeMessage implements RawMessage {
  public static final short TYPE = 14;

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
