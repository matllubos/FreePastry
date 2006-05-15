package rice.p2p.glacier.v2.messaging;

import java.io.IOException;

import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.commonapi.rawserialization.*;

public class GlacierNeighborRequestMessage extends GlacierMessage {
  public static final short TYPE = 3;

  protected IdRange requestedRange;

  public GlacierNeighborRequestMessage(int uid, IdRange requestedRange, NodeHandle source, Id dest, char tag) {
    super(uid, source, dest, false, tag);

    this.requestedRange = requestedRange;
  }

  public IdRange getRequestedRange() {
    return requestedRange;
  }

  public String toString() {
    return "[GlacierNeighborRequest for " + requestedRange +"]";
  }  
  
  /***************** Raw Serialization ***************************************/
  public short getType() {
    return TYPE; 
  }
  
  public void serialize(OutputBuffer buf) throws IOException {
    buf.writeByte((byte)0); // version    
    super.serialize(buf); 
    requestedRange.serialize(buf);
  }
  
  public static GlacierNeighborRequestMessage build(InputBuffer buf, Endpoint endpoint) throws IOException {
    byte version = buf.readByte();
    switch(version) {
      case 0:
        return new GlacierNeighborRequestMessage(buf, endpoint);
      default:
        throw new IOException("Unknown Version: "+version);
    }
  }
    
  private GlacierNeighborRequestMessage(InputBuffer buf, Endpoint endpoint) throws IOException {
    super(buf, endpoint);
    requestedRange = endpoint.readIdRange(buf);
  }
}

