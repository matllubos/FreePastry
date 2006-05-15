package rice.p2p.glacier.v2.messaging;

import java.io.IOException;

import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.commonapi.rawserialization.*;
import rice.p2p.glacier.*;

public class GlacierRangeResponseMessage extends GlacierMessage {
  public static final short TYPE = 8;

  protected IdRange commonRange;

  public GlacierRangeResponseMessage(int uid, IdRange commonRange, NodeHandle source, Id dest, char tag) {
    super(uid, source, dest, true, tag);

    this.commonRange = commonRange;
  }

  public IdRange getCommonRange() {
    return commonRange;
  }

  public String toString() {
    return "[GlacierRangeResponse to UID#" + getUID() + ", range="+commonRange+"]";
  }
  
  /***************** Raw Serialization ***************************************/
  public short getType() {
    return TYPE; 
  }
  
  public void serialize(OutputBuffer buf) throws IOException {
    buf.writeByte((byte)0); // version    
    super.serialize(buf);
    commonRange.serialize(buf);
  }

  public static GlacierRangeResponseMessage build(InputBuffer buf, Endpoint endpoint) throws IOException {
    byte version = buf.readByte();
    switch(version) {
      case 0:
        return new GlacierRangeResponseMessage(buf, endpoint);
      default:
        throw new IOException("Unknown Version: "+version);
    }
  }
    
  private GlacierRangeResponseMessage(InputBuffer buf, Endpoint endpoint) throws IOException {
    super(buf, endpoint);
    commonRange = endpoint.readIdRange(buf);
  }
}

