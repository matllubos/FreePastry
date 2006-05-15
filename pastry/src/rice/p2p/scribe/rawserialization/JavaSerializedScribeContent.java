/*
 * Created on Mar 21, 2006
 */
package rice.p2p.scribe.rawserialization;

import java.io.*;

import rice.p2p.commonapi.rawserialization.OutputBuffer;
import rice.p2p.scribe.ScribeContent;

public class JavaSerializedScribeContent implements RawScribeContent {
  public static final short TYPE = 0;
  
  public ScribeContent content;
  
  public JavaSerializedScribeContent(ScribeContent content) {
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
//    System.out.println("JavaSerializedScribeContent.serialize() "+content+" length:"+temp.length);
//    new Exception("Stack Trace").printStackTrace();
  }

  public short getType() {
    return 0;
  }
  
  public String toString() {
    return "JSSC ["+content+"]"; 
  }

  public ScribeContent getContent() {
    return content;
  }
}
