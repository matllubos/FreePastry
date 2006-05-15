package rice.p2p.glacier.v2.messaging;

import java.io.IOException;

import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.commonapi.rawserialization.*;
import rice.p2p.glacier.*;

public class GlacierRangeQueryMessage extends GlacierMessage {
  public static final short TYPE = 7;

  protected IdRange requestedRange;

  public GlacierRangeQueryMessage(int uid, IdRange requestedRange, NodeHandle source, Id dest, char tag) {
    super(uid, source, dest, false, tag);

    this.requestedRange = requestedRange;
  }

  public IdRange getRequestedRange() {
    return requestedRange;
  }

  public String toString() {
    return "[GlacierRangeQuery #"+getUID()+" for " + requestedRange + "]";
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
  
  public static GlacierRangeQueryMessage build(InputBuffer buf, Endpoint endpoint) throws IOException {
    byte version = buf.readByte();
    switch(version) {
      case 0:
        return new GlacierRangeQueryMessage(buf, endpoint);
      default:
        throw new IOException("Unknown Version: "+version);
    }
  }
    
  private GlacierRangeQueryMessage(InputBuffer buf, Endpoint endpoint) throws IOException {
    super(buf, endpoint);
    requestedRange = endpoint.readIdRange(buf);
  }
}

