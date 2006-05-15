/*
 * Created on Mar 21, 2006
 */
package rice.p2p.past.gc.rawserialization;

import java.io.*;

import rice.p2p.commonapi.Id;
import rice.p2p.commonapi.rawserialization.OutputBuffer;
import rice.p2p.past.*;
import rice.p2p.past.gc.*;

public class JavaSerializedGCPastContent implements RawGCPastContent {
  public static final short TYPE = 0;
  
  public GCPastContent content;
  
  public JavaSerializedGCPastContent(GCPastContent content) {
    this.content = content;
  }
  
  public void serialize(OutputBuffer buf) throws IOException {
    
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ObjectOutputStream oos = new ObjectOutputStream(baos);

    // write out object and find its length
    oos.writeObject(content);
    oos.close();
    
    byte[] temp = baos.toByteArray();
    buf.writeInt(temp.length);
    buf.write(temp, 0, temp.length);
//    System.out.println("JavaSerializedGCPastContent.serialize() "+content+" length:"+temp.length);
//    new Exception("Stack Trace").printStackTrace();
  }

  public short getType() {
    return TYPE;
  }
  
  public String toString() {
    return "JSPC ["+content+"]"; 
  }

  public PastContent getContent() {
    return content;
  }

  public PastContent checkInsert(Id id, PastContent existingContent) throws PastException {
    return content.checkInsert(id, existingContent);
  }

  public PastContentHandle getHandle(Past local) {
    return content.getHandle(local);
  }

  public Id getId() {
    return content.getId();
  }

  public boolean isMutable() {
    return content.isMutable();
  }

  public long getVersion() {
    return content.getVersion();
  }

  public GCPastContentHandle getHandle(GCPast local, long expiration) {
    return content.getHandle(local, expiration);
  }

  public GCPastMetadata getMetadata(long expiration) {
    return content.getMetadata(expiration);
  }
}
