/*
 * Created on Mar 21, 2006
 */
package rice.p2p.past.rawserialization;

import java.io.*;

import rice.p2p.commonapi.Id;
import rice.p2p.commonapi.rawserialization.OutputBuffer;
import rice.p2p.past.*;

public class JavaSerializedPastContent implements RawPastContent {
  public static final short TYPE = 0;
  
  public PastContent content;
  
  public JavaSerializedPastContent(PastContent content) {
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
//    System.out.println("JavaSerializedPastContent.serialize() "+content+" length:"+temp.length);
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
}
