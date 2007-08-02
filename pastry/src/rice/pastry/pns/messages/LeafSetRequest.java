package rice.pastry.pns.messages;

import java.io.IOException;

import rice.p2p.commonapi.rawserialization.InputBuffer;
import rice.p2p.commonapi.rawserialization.OutputBuffer;
import rice.pastry.NodeHandle;
import rice.pastry.messaging.Message;
import rice.pastry.messaging.PRawMessage;

public class LeafSetRequest extends PRawMessage {
  public static final short TYPE = 1;
  
  public LeafSetRequest(NodeHandle nodeHandle, int dest) {
    super(dest);
    if (nodeHandle == null) throw new IllegalArgumentException("nodeHandle == null!");
    setSender(nodeHandle);    
  }

  public static LeafSetRequest build(InputBuffer buf, NodeHandle sender, int dest) throws IOException {
    byte version = buf.readByte();
    if (version == 0)
      return new LeafSetRequest(sender, dest);
    throw new IllegalStateException("Unknown version:"+version);
  }

  public short getType() {
    return TYPE;
  }

  public void serialize(OutputBuffer buf) throws IOException {
    buf.writeByte((byte)0); // version
  }

}
