package rice.p2p.glacier.v2.messaging;

import java.io.IOException;

import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.commonapi.rawserialization.*;
import rice.p2p.glacier.VersionKey;

public class GlacierRefreshPatchMessage extends GlacierMessage {
  public static final short TYPE = 10;


  protected VersionKey[] keys;
  protected long[] lifetimes;
  protected byte[][] signatures;

  public GlacierRefreshPatchMessage(int uid, VersionKey[] keys, long[] lifetimes, byte[][] signatures, NodeHandle source, Id dest, char tag) {
    super(uid, source, dest, false, tag);

    this.keys = keys;
    this.lifetimes = lifetimes;
    this.signatures = signatures;
  }

  public int numKeys() {
    return keys.length;
  }

  public VersionKey getKey(int index) {
    return keys[index];
  }

  public VersionKey[] getAllKeys() {
    return keys;
  }

  public long getLifetime(int index) {
    return lifetimes[index];
  }

  public byte[] getSignature(int index) {
    return signatures[index];
  }

  public String toString() {
    return "[GlacierRefreshPatch for " + keys[0] + " ("+(numKeys()-1)+" more keys)]";
  }
  
  /***************** Raw Serialization ***************************************/
  public short getType() {
    return TYPE; 
  }
  
  public void serialize(OutputBuffer buf) throws IOException {
    buf.writeByte((byte)0); // version    
    super.serialize(buf); 
    
    buf.writeInt(lifetimes.length);
    for (int i = 0; i < lifetimes.length; i++) {
      buf.writeLong(lifetimes[i]); 
    }
    
    buf.writeInt(keys.length);
    for (int i = 0; i < keys.length; i++) {
      keys[i].serialize(buf); 
    }
    
    buf.writeInt(signatures.length);
    for (int i = 0; i < signatures.length; i++) {
      buf.writeInt(signatures[i].length);
      buf.write(signatures[i], 0, signatures[i].length);
    }    
  }

  public static GlacierRefreshPatchMessage build(InputBuffer buf, Endpoint endpoint) throws IOException {
    byte version = buf.readByte();
    switch(version) {
      case 0:
        return new GlacierRefreshPatchMessage(buf, endpoint);
      default:
        throw new IOException("Unknown Version: "+version);
    }
  }
    
  private GlacierRefreshPatchMessage(InputBuffer buf, Endpoint endpoint) throws IOException {
    super(buf, endpoint);
    lifetimes = new long[buf.readInt()];
    for (int i = 0; i < lifetimes.length; i++) {
      lifetimes[i] = buf.readLong(); 
    }
    keys = new VersionKey[buf.readInt()];
    for (int i = 0; i < keys.length; i++) {
      keys[i] = new VersionKey(buf, endpoint); 
    }    
    signatures = new byte[buf.readInt()][];
    for (int i = 0; i < signatures.length; i++) {
      signatures[i] = new byte[buf.readInt()];       
      buf.read(signatures[i]);
    }
  }
}

