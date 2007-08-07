package rice.pastry.pns.messages;

import java.io.IOException;

import rice.p2p.commonapi.rawserialization.InputBuffer;
import rice.p2p.commonapi.rawserialization.OutputBuffer;
import rice.pastry.NodeHandle;
import rice.pastry.messaging.Message;
import rice.pastry.messaging.PRawMessage;

public class RouteRowRequest extends PRawMessage {
  public static final short TYPE = 3;
  public short index;
  
  public RouteRowRequest(NodeHandle nodeHandle, short index, int dest) {
    super(dest);
    if (nodeHandle == null) throw new IllegalArgumentException("nodeHandle == null!");
    setSender(nodeHandle);    
    this.index = index;
    setPriority(HIGH_PRIORITY);
  }

  public static rice.p2p.commonapi.Message build(InputBuffer buf, NodeHandle sender, int dest) throws IOException {
    byte version = buf.readByte();
    if (version == 0)
      return new RouteRowRequest(sender, buf.readShort(), dest);
    throw new IllegalStateException("Unknown version:"+version);
  }

  public short getType() {
    return TYPE;
  }

  public void serialize(OutputBuffer buf) throws IOException {
    buf.writeByte((byte)0); // version
    buf.writeShort(index);    
  }

}
