package rice.p2p.glacier.v2.messaging;

import java.io.IOException;

import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.commonapi.rawserialization.*;
import rice.p2p.glacier.*;

public class GlacierFetchMessage extends GlacierMessage {
  public static final short TYPE = 2;

  protected FragmentKey[] keys;
  protected int request;

  public static final int FETCH_FRAGMENT = 1;
  public static final int FETCH_MANIFEST = 2;
  public static final int FETCH_FRAGMENT_AND_MANIFEST = FETCH_FRAGMENT | FETCH_MANIFEST;

  public GlacierFetchMessage(int uid, FragmentKey key, int request, NodeHandle source, Id dest, char tag) {
    this(uid, new FragmentKey[] { key }, request, source, dest, tag);
  }

  public GlacierFetchMessage(int uid, FragmentKey[] keys, int request, NodeHandle source, Id dest, char tag) {
    super(uid, source, dest, false, tag);

    this.keys = keys;
    this.request = request;
  }

  public FragmentKey[] getAllKeys() {
    return keys;
  }

  public int getRequest() {
    return request;
  }

  public int getNumKeys() {
    return keys.length;
  }

  public FragmentKey getKey(int index) {
    return keys[index];
  }

  public String toString() {
    return "[GlacierFetch for " + keys[0] + " and "+(keys.length-1)+" other keys, req="+request+"]";
  }
  
  /***************** Raw Serialization ***************************************/
  public short getType() {
    return TYPE; 
  }
  
  public void serialize(OutputBuffer buf) throws IOException {
    buf.writeByte((byte)0); // version    
    super.serialize(buf);
    
    buf.writeInt(request);
    int l = keys.length;
    buf.writeInt(l);
    for (int i = 0; i < l; i++) {
      keys[i].serialize(buf); 
    }    
  }
  
  public static GlacierFetchMessage build(InputBuffer buf, Endpoint endpoint) throws IOException {
    byte version = buf.readByte();
    switch(version) {
      case 0:
        return new GlacierFetchMessage(buf, endpoint);
      default:
        throw new IOException("Unknown Version: "+version);
    }
  }
    
  private GlacierFetchMessage(InputBuffer buf, Endpoint endpoint) throws IOException {
    super(buf, endpoint); 
    request = buf.readInt();
    int l = buf.readInt();
    keys = new FragmentKey[l];
    for (int i = 0; i < l; i++) {
      keys[i] = new FragmentKey(buf, endpoint); 
    }        
  }
}

