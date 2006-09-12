/*
 * Created on Mar 21, 2006
 */
package rice.p2p.scribe.rawserialization;

import java.io.*;

import rice.p2p.commonapi.Endpoint;
import rice.p2p.commonapi.rawserialization.InputBuffer;
import rice.p2p.scribe.ScribeContent;
import rice.p2p.util.rawserialization.JavaDeserializer;

/**
 * uses p2p.util.JavaDeserializer to deserialize ScribeContent using Java Serialization
 * 
 * @author Jeff Hoye
 */
public class JavaScribeContentDeserializer implements ScribeContentDeserializer {
  public JavaScribeContentDeserializer() {
    
  }

  public ScribeContent deserializeScribeContent(InputBuffer buf, Endpoint endpoint, short contentType) throws IOException {    
    byte[] array = new byte[buf.readInt()];
    buf.read(array);
    
    ObjectInputStream ois = new JavaDeserializer(new ByteArrayInputStream(array), endpoint);

    try {
      Object o = ois.readObject();
      ScribeContent ret = (ScribeContent)o;
      return ret;
    } catch (ClassNotFoundException e) {
      throw new RuntimeException("Unknown class type in message - cant deserialize.", e);
    }    
  }
}
