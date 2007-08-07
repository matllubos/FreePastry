package rice.pastry.pns.messages;

import java.io.IOException;

import rice.p2p.commonapi.NodeHandle;
import rice.p2p.commonapi.rawserialization.InputBuffer;
import rice.p2p.commonapi.rawserialization.OutputBuffer;
import rice.pastry.NodeHandleFactory;
import rice.pastry.leafset.LeafSet;
import rice.pastry.messaging.Message;
import rice.pastry.messaging.PRawMessage;

public class LeafSetResponse extends PRawMessage {
  public static final short TYPE = 2;
  
  public LeafSet leafset;
  
  public LeafSetResponse(LeafSet leafset, int dest) {
    super(dest);
    this.leafset = leafset;
    setPriority(HIGH_PRIORITY);
  }

  public short getType() {
    return TYPE;
  }

  public void serialize(OutputBuffer buf) throws IOException {
    buf.writeByte((byte)0); // version
    leafset.serialize(buf);
  }

  public static rice.p2p.commonapi.Message build(InputBuffer buf, NodeHandleFactory nhf, int dest) throws IOException {
    byte version = buf.readByte();
    switch(version) {
    case 0:
      return new LeafSetResponse(LeafSet.build(buf, nhf), dest);    
    }
    throw new IllegalStateException("Unknown version:"+version);
  }
}
