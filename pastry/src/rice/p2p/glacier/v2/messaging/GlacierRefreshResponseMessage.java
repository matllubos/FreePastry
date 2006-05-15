package rice.p2p.glacier.v2.messaging;

import java.io.IOException;

import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.commonapi.rawserialization.*;
import rice.p2p.glacier.*;

public class GlacierRefreshResponseMessage extends GlacierMessage {
  public static final short TYPE = 12;

  protected IdRange range;
  protected boolean online;

  public GlacierRefreshResponseMessage(int uid, IdRange range, boolean online, NodeHandle source, Id dest, char tag) {
    super(uid, source, dest, true, tag);

    this.range = range;
    this.online = online;
  }

  public IdRange getRange() {
    return range;
  }
  
  public boolean isOnline() {
    return online;
  }

  public String toString() {
    return "[GlacierRefreshResponse for "+range+", online="+online+"]";
  }
  
  /***************** Raw Serialization ***************************************/
  public short getType() {
    return TYPE; 
  }
  
  public void serialize(OutputBuffer buf) throws IOException {
    buf.writeByte((byte)0); // version    
    super.serialize(buf);
    range.serialize(buf);
    buf.writeBoolean(online);
  }

  public static GlacierRefreshResponseMessage build(InputBuffer buf, Endpoint endpoint) throws IOException {
    byte version = buf.readByte();
    switch(version) {
      case 0:
        return new GlacierRefreshResponseMessage(buf, endpoint);
      default:
        throw new IOException("Unknown Version: "+version);
    }
  }
    
  private GlacierRefreshResponseMessage(InputBuffer buf, Endpoint endpoint) throws IOException {
    super(buf, endpoint);
    range = endpoint.readIdRange(buf);
    online = buf.readBoolean();
  }
}

