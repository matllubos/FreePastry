/*
 * Created on Mar 21, 2006
 */
package rice.p2p.past.rawserialization;

import java.io.*;

import rice.p2p.commonapi.Endpoint;
import rice.p2p.commonapi.rawserialization.InputBuffer;
import rice.p2p.past.PastContent;
import rice.p2p.util.JavaDeserializer;

/**
 * uses p2p.util.JavaDeserializer to deserialize ScribeContent using Java Serialization
 * 
 * @author Jeff Hoye
 */
public class JavaPastContentDeserializer implements PastContentDeserializer {
  public JavaPastContentDeserializer() {
    
  }

  public PastContent deserializePastContent(InputBuffer buf, Endpoint endpoint, short contentType) throws IOException {    
    byte[] array = new byte[buf.readInt()];
    buf.read(array);
    
    ObjectInputStream ois = new JavaDeserializer(new ByteArrayInputStream(array), endpoint);

    try {
      Object o = ois.readObject();
      PastContent ret = (PastContent)o;
      return ret;
    } catch (ClassNotFoundException e) {
      throw new RuntimeException("Unknown class type in message - cant deserialize.", e);
    }    
  }
}
