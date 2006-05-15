package rice.p2p.glacier.v2.messaging;

import java.io.IOException;

import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.commonapi.rawserialization.*;

public class GlacierNeighborResponseMessage extends GlacierMessage {
  public static final short TYPE = 4;

  protected Id[] neighbors;
  protected long[] lastSeen;

  public GlacierNeighborResponseMessage(int uid, Id[] neighbors, long[] lastSeen, NodeHandle source, Id dest, char tag) {
    super(uid, source, dest, true, tag);

    this.neighbors = neighbors;
    this.lastSeen = lastSeen;
  }

  public int numNeighbors() {
    if ((neighbors == null) || (lastSeen == null))
      return 0;
      
    if (lastSeen.length < neighbors.length)
      return lastSeen.length;
      
    return neighbors.length;
  }

  public Id getNeighbor(int index) {
    return neighbors[index];
  }
  
  public long getLastSeen(int index) {
    return lastSeen[index];
  }

  public String toString() {
    return "[GlacierNeighborResponse with "+numNeighbors()+" keys]";
  }
  
  /***************** Raw Serialization ***************************************/
  public short getType() {
    return TYPE; 
  }

  public void serialize(OutputBuffer buf) throws IOException {
    buf.writeByte((byte)0); // version    
    super.serialize(buf);
    buf.writeInt(lastSeen.length);
    for (int i = 0; i < lastSeen.length; i++) {
      buf.writeLong(lastSeen[i]); 
    }
    
    buf.writeInt(neighbors.length);
    for (int i = 0; i < neighbors.length; i++) {
      buf.writeShort(neighbors[i].getType());
      neighbors[i].serialize(buf); 
    }
  }
  
  
  public static GlacierNeighborResponseMessage build(InputBuffer buf, Endpoint endpoint) throws IOException {
    byte version = buf.readByte();
    switch(version) {
      case 0:
        return new GlacierNeighborResponseMessage(buf, endpoint);
      default:
        throw new IOException("Unknown Version: "+version);
    }
  }
    
  private GlacierNeighborResponseMessage(InputBuffer buf, Endpoint endpoint) throws IOException {
    super(buf, endpoint);
    lastSeen = new long[buf.readInt()];
    for (int i = 0; i < lastSeen.length; i++) {
      lastSeen[i] = buf.readLong(); 
    }
    neighbors = new Id[buf.readInt()];
    for (int i = 0; i < lastSeen.length; i++) {
      neighbors[i] = endpoint.readId(buf, buf.readShort()); 
    }
  }
}

