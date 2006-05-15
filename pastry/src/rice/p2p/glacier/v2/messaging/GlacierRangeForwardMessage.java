package rice.p2p.glacier.v2.messaging;

import java.io.IOException;

import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.commonapi.rawserialization.*;
import rice.p2p.glacier.*;

public class GlacierRangeForwardMessage extends GlacierMessage {
  public static final short TYPE = 6;

  protected IdRange requestedRange;
  protected NodeHandle requestor;

  public GlacierRangeForwardMessage(int uid, IdRange requestedRange, NodeHandle requestor, NodeHandle source, Id dest, char tag) {
    super(uid, source, dest, false, tag);

    this.requestedRange = requestedRange;
    this.requestor = requestor;
  }

  public IdRange getRequestedRange() {
    return requestedRange;
  }

  public NodeHandle getRequestor() {
    return requestor;
  }

  public String toString() {
    return "[GlacierRangeForward #"+getUID()+" for " + requestedRange + " by " + requestor + "]";
  }
  
  /***************** Raw Serialization ***************************************/
  public short getType() {
    return TYPE; 
  }
  
  public void serialize(OutputBuffer buf) throws IOException {
    buf.writeByte((byte)0); // version    
    super.serialize(buf); 
    requestedRange.serialize(buf);
    requestor.serialize(buf);
  }
  
  public static GlacierRangeForwardMessage build(InputBuffer buf, Endpoint endpoint) throws IOException {
    byte version = buf.readByte();
    switch(version) {
      case 0:
        return new GlacierRangeForwardMessage(buf, endpoint);
      default:
        throw new IOException("Unknown Version: "+version);
    }
  }
    
  private GlacierRangeForwardMessage(InputBuffer buf, Endpoint endpoint) throws IOException {
    super(buf, endpoint);
    requestedRange = endpoint.readIdRange(buf);
    requestor = endpoint.readNodeHandle(buf);
  }  
}

