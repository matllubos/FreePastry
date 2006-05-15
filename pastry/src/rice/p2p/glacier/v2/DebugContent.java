package rice.p2p.glacier.v2;

import rice.p2p.past.*;
import rice.p2p.past.gc.*;
import rice.p2p.past.gc.rawserialization.RawGCPastContent;
import rice.p2p.commonapi.*;
import rice.p2p.glacier.*;
import java.io.*;

public class DebugContent implements GCPastContent {

  protected Id myId;
  protected boolean isMutable;
  protected long version;
  protected transient byte[] payload;

  public DebugContent(Id id, boolean isMutable, long version, byte[] payload) {
    myId = id;
    this.isMutable = isMutable;
    this.version = version;
    this.payload = payload;
  }

  public PastContent checkInsert(Id id, PastContent existingContent) throws PastException {
    if (!isMutable ||  !(existingContent instanceof DebugContent))
      return this;
      
    DebugContent dc = (DebugContent) existingContent;
    return (this.version > dc.version) ? this : dc;
  }

  public long getVersion() {
    return version;
  }
  
  public PastContentHandle getHandle(Past local) {
    return new DebugContentHandle(myId, version, GCPast.INFINITY_EXPIRATION, local.getLocalNodeHandle());
  }
  
  public GCPastContentHandle getHandle(GCPast local, long expiration) {
    return new DebugContentHandle(myId, version, expiration, local.getLocalNodeHandle());
  }
  
  public Id getId() {
    return myId;
  }
  
  public boolean isMutable() {
    return this.isMutable;
  }
  
  public byte[] getPayload() {
    return payload;
  }

  private void writeObject(ObjectOutputStream oos) throws IOException {
    oos.defaultWriteObject();
    oos.writeInt(payload.length);
    oos.write(payload);
  }
  
  private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
    ois.defaultReadObject();
    payload = new byte[ois.readInt()];
    ois.readFully(payload, 0, payload.length);
  }
  
  public GCPastMetadata getMetadata(long expiration) {
    return new GCPastMetadata(expiration);
  }
}

