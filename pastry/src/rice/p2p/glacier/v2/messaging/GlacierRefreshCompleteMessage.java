package rice.p2p.glacier.v2.messaging;

import java.io.IOException;

import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.commonapi.rawserialization.*;
import rice.p2p.glacier.VersionKey;

public class GlacierRefreshCompleteMessage extends GlacierMessage {
  public static final short TYPE = 9;

  protected VersionKey[] keys;
  protected int[] updates;

  public GlacierRefreshCompleteMessage(int uid, VersionKey[] keys, int[] updates, NodeHandle source, Id dest, char tag) {
    super(uid, source, dest, true, tag);

    this.keys = keys;
    this.updates = updates;
  }

  public int numKeys() {
    return keys.length;
  }

  public VersionKey getKey(int index) {
    return keys[index];
  }

  public long getUpdates(int index) {
    return updates[index];
  }

  public String toString() {
    return "[GlacierRefreshComplete for " + keys[0] + " ("+(numKeys()-1)+" more keys)]";
  }
  
  /***************** Raw Serialization ***************************************/
  public short getType() {
    return TYPE; 
  }
  
  public void serialize(OutputBuffer buf) throws IOException {
    buf.writeByte((byte)0); // version    
    super.serialize(buf);
    buf.writeInt(updates.length);
    for (int i = 0; i < updates.length; i++) {
      buf.writeInt(updates[i]); 
    }
    
    buf.writeInt(keys.length);
    for (int i = 0; i < keys.length; i++) {
      keys[i].serialize(buf); 
    }
  }
  
  public static GlacierRefreshCompleteMessage build(InputBuffer buf, Endpoint endpoint) throws IOException {
    byte version = buf.readByte();
    switch(version) {
      case 0:
        return new GlacierRefreshCompleteMessage(buf, endpoint);
      default:
        throw new IOException("Unknown Version: "+version);
    }
  }
    
  private GlacierRefreshCompleteMessage(InputBuffer buf, Endpoint endpoint) throws IOException {
    super(buf, endpoint);
    updates = new int[buf.readInt()];
    for (int i = 0; i < updates.length; i++) {
      updates[i] = buf.readInt(); 
    }
    keys = new VersionKey[buf.readInt()];
    for (int i = 0; i < keys.length; i++) {
      keys[i] = new VersionKey(buf, endpoint); 
    }
  }
}

