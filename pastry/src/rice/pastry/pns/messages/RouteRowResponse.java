package rice.pastry.pns.messages;

import java.io.IOException;

import rice.p2p.commonapi.rawserialization.InputBuffer;
import rice.p2p.commonapi.rawserialization.OutputBuffer;
import rice.pastry.NodeHandle;
import rice.pastry.NodeHandleFactory;
import rice.pastry.PastryNode;
import rice.pastry.messaging.Message;
import rice.pastry.messaging.PRawMessage;
import rice.pastry.routing.RouteSet;

public class RouteRowResponse extends PRawMessage {

  public static final short TYPE = 4;
  public short index;
  public RouteSet[] row;

  public RouteRowResponse(NodeHandle sender, short index, RouteSet[] row, int address) {
    super(address);
    if (sender == null) throw new IllegalArgumentException("sender == null!");
    setSender(sender);    
    this.index = index;
    this.row = row;
    setPriority(HIGH_PRIORITY);
  }

  public void serialize(OutputBuffer buf) throws IOException {
    buf.writeByte((byte)0); // version    
    buf.writeShort(index);
    buf.writeInt(row.length);
    for (int i = 0; i < row.length; i++) {
      if (row[i] == null) {
        buf.writeBoolean(false);
      } else {
        buf.writeBoolean(true);
        row[i].serialize(buf); 
      }
    }    
  }

  public RouteRowResponse(InputBuffer buf, PastryNode localNode, NodeHandle sender, int dest) throws IOException {
    super(dest);
    byte version = buf.readByte();
    switch(version) {
      case 0:
        setSender(sender);
        index = buf.readShort();
        int numRouteSets = buf.readInt();
        row = new RouteSet[numRouteSets];
        for (int i = 0; i<numRouteSets; i++) {      
          if (buf.readBoolean()) {
            row[i] = new RouteSet(buf, localNode, localNode); 
          }
        }
        break;
      default:
        throw new IOException("Unknown Version: "+version);
    }     
  }

  public short getType() {
    return TYPE;
  }

}
