package rice.p2p.glacier.v2.messaging;

import java.io.IOException;

import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.commonapi.rawserialization.*;
import rice.p2p.glacier.*;
import rice.p2p.glacier.v2.BloomFilter;

public class GlacierSyncMessage extends GlacierMessage {
  public static final short TYPE = 14;

  protected IdRange range;
  protected int offsetFID;
  protected BloomFilter BloomFilter;

  public GlacierSyncMessage(int uid, IdRange range, int offsetFID, BloomFilter BloomFilter, NodeHandle source, Id dest, char tag) {
    super(uid, source, dest, false, tag);

    this.range = range;
    this.offsetFID = offsetFID;
    this.BloomFilter = BloomFilter;
  }

  public int getOffsetFID() {
    return offsetFID;
  }

  public IdRange getRange() {
    return range;
  }

  public BloomFilter getBloomFilter() {
    return BloomFilter;
  }

  public String toString() {
    return "[GlacierSync for range "+range+", offset "+offsetFID+"]";
  }
  
  /***************** Raw Serialization ***************************************/
  public short getType() {
    return TYPE; 
  }
  
  public void serialize(OutputBuffer buf) throws IOException {
    buf.writeByte((byte)0); // version    
    super.serialize(buf);
    buf.writeInt(offsetFID);
    range.serialize(buf);
    BloomFilter.serialize(buf);
  }
  
  public static GlacierSyncMessage build(InputBuffer buf, Endpoint endpoint) throws IOException {
    byte version = buf.readByte();
    switch(version) {
      case 0:
        return new GlacierSyncMessage(buf, endpoint);
      default:
        throw new IOException("Unknown Version: "+version);
    }
  }
    
  private GlacierSyncMessage(InputBuffer buf, Endpoint endpoint) throws IOException {
    super(buf, endpoint);
    offsetFID = buf.readInt();
    range = endpoint.readIdRange(buf);
    BloomFilter = new BloomFilter(buf);
  }
}

