package rice.p2p.glacier.v2.messaging;

import java.io.IOException;

import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.commonapi.rawserialization.*;
import rice.p2p.glacier.*;

public class GlacierRefreshProbeMessage extends GlacierMessage {
  public static final short TYPE = 11;

  protected Id requestedId;

  public GlacierRefreshProbeMessage(int uid, Id requestedId, NodeHandle source, Id dest, char tag) {
    super(uid, source, dest, false, tag);

    this.requestedId = requestedId;
  }

  public Id getRequestedId() {
    return requestedId;
  }

  public String toString() {
    return "[GlacierRefreshProbe #"+getUID()+" for " + requestedId + "]";
  }
  
  /***************** Raw Serialization ***************************************/
  public short getType() {
    return TYPE; 
  }
  
  public void serialize(OutputBuffer buf) throws IOException {
    buf.writeByte((byte)0); // version    
    super.serialize(buf); 
    buf.writeShort(requestedId.getType());
    requestedId.serialize(buf);
  }

  public static GlacierRefreshProbeMessage build(InputBuffer buf, Endpoint endpoint) throws IOException {
    byte version = buf.readByte();
    switch(version) {
      case 0:
        return new GlacierRefreshProbeMessage(buf, endpoint);
      default:
        throw new IOException("Unknown Version: "+version);
    }
  }
    
  private GlacierRefreshProbeMessage(InputBuffer buf, Endpoint endpoint) throws IOException {
    super(buf, endpoint);
    requestedId = endpoint.readId(buf, buf.readShort());
  }
}

