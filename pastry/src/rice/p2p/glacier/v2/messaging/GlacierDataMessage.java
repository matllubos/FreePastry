package rice.p2p.glacier.v2.messaging;

import java.io.IOException;

import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.commonapi.rawserialization.*;
import rice.p2p.glacier.v2.Manifest;
import rice.p2p.glacier.Fragment;
import rice.p2p.glacier.FragmentKey;

public class GlacierDataMessage extends GlacierMessage {

  public static final short TYPE = 1;
  
  protected FragmentKey[] keys;
  protected Fragment[] fragments;
  protected Manifest[] manifests;

  public GlacierDataMessage(int uid, FragmentKey key, Fragment fragment, Manifest manifest, NodeHandle source, Id dest, boolean isResponse, char tag) {
    this(uid, new FragmentKey[] { key }, new Fragment[] { fragment }, new Manifest[] { manifest }, source, dest, isResponse, tag);
  }

  public GlacierDataMessage(int uid, FragmentKey[] keys, Fragment[] fragments, Manifest[] manifests, NodeHandle source, Id dest, boolean isResponse, char tag) {
    super(uid, source, dest, isResponse, tag);

    this.keys = keys;
    this.fragments = fragments;
    this.manifests = manifests;
  }

  public int numKeys() {
    return keys.length;
  }

  public FragmentKey getKey(int index) {
    return keys[index];
  }

  public Fragment getFragment(int index) {
    return fragments[index];
  }

  public Manifest getManifest(int index) {
    return manifests[index];
  }

  public String toString() {
    return "[GlacierData for " + keys[0] + " ("+(numKeys()-1)+" more keys)]";
  }
  

  /***************** Raw Serialization ***************************************/
  public short getType() {
    return TYPE;
  }
  
  public void serialize(OutputBuffer buf) throws IOException {
    buf.writeByte((byte)0); // version    
    super.serialize(buf);
    
    int l = fragments.length;
    buf.writeInt(l);
    for (int i = 0; i < l; i++) {
      if (fragments[i] == null) {
        buf.writeBoolean(false);        
      } else {
        buf.writeBoolean(true);
        fragments[i].serialize(buf);
      }
    }
    
    l = keys.length;
    buf.writeInt(l);
    for (int i = 0; i < l; i++) {
      keys[i].serialize(buf);
    }
    
    l = manifests.length;
    buf.writeInt(l);
    for (int i = 0; i < l; i++) {
      if (manifests[i] == null) {
        buf.writeBoolean(false);        
      } else {
        buf.writeBoolean(true);
        manifests[i].serialize(buf);
      }
    }
  }
  
  public static GlacierDataMessage build(InputBuffer buf, Endpoint endpoint) throws IOException {
    byte version = buf.readByte();
    switch(version) {
      case 0:
        return new GlacierDataMessage(buf, endpoint);
      default:
        throw new IOException("Unknown Version: "+version);
    }
  }
    
  private GlacierDataMessage(InputBuffer buf, Endpoint endpoint) throws IOException {
    super(buf, endpoint); 

    fragments = new Fragment[buf.readInt()];
    for (int i = 0; i < fragments.length; i++) {
      if (buf.readBoolean())
        fragments[i] = new Fragment(buf);
    }
    
    keys = new FragmentKey[buf.readInt()];
    for (int i = 0; i < keys.length; i++) {
      keys[i] = new FragmentKey(buf, endpoint);
    }
    
    manifests = new Manifest[buf.readInt()];
    for (int i = 0; i < manifests.length; i++) {
      if (buf.readBoolean())
        manifests[i] = new Manifest(buf);
    }
  }
}

