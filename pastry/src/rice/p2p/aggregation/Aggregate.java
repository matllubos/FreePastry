package rice.p2p.aggregation;

import rice.p2p.past.gc.GCPastContent;
import rice.p2p.past.*;
import rice.p2p.past.gc.*;
import rice.p2p.commonapi.Id;
import rice.p2p.glacier.VersionKey;
import java.security.*;
import java.io.*;

public class Aggregate implements GCPastContent {
  protected GCPastContent[] components;
  protected Id[] pointers;
  protected Id myId;
  
  private static final long serialVersionUID = -4891386773008082L;
  
  public Aggregate(GCPastContent[] components, Id[] pointers) {
    this.components = components;
    this.myId = null;
    this.pointers = pointers;
  }
  
  public void setId(Id myId) {
    this.myId = myId;
  }
  
  public Id getId() {
    return myId;
  }
  
  public Id[] getPointers() {
    return pointers;
  }
  
  public int numComponents() {
    return components.length;
  }
  
  public GCPastContent getComponent(int index) {
    return components[index];
  }
  
  public long getVersion() {
    return 0;
  }
  
  public boolean isMutable() {
    return false;
  }
  
  public PastContent checkInsert(rice.p2p.commonapi.Id id, PastContent existingContent) throws PastException {
    if (existingContent == null) {
      return this;
    } else {
      return existingContent;
    }
  }

  public byte[] getContentHash() {
    byte[] bytes = null;
    
    try {
      ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
      ObjectOutputStream objectStream = new ObjectOutputStream(byteStream);

      objectStream.writeObject(components);
      objectStream.writeObject(pointers);
      objectStream.flush();

      bytes = byteStream.toByteArray();
    } catch (IOException ioe) {
      return null;
    }
    
    MessageDigest md = null;
    try {
      md = MessageDigest.getInstance("SHA");
    } catch (NoSuchAlgorithmException e) {
      return null;
    }

    md.reset();
    md.update(bytes);
    
    return md.digest();
  }

  public PastContentHandle getHandle(Past local) {
    return new AggregateHandle(local.getLocalNodeHandle(), myId, getVersion(), GCPast.INFINITY_EXPIRATION);
  }

  public GCPastContentHandle getHandle(GCPast local, long expiration) {
    return new AggregateHandle(local.getLocalNodeHandle(), myId, getVersion(), expiration);
  }

  public GCPastMetadata getMetadata(long expiration) {
    return new GCPastMetadata(expiration);
  }
};
