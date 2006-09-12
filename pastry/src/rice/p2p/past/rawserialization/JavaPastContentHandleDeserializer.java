/*
 * Created on Mar 23, 2006
 */
package rice.p2p.past.rawserialization;

import java.io.*;

import rice.p2p.commonapi.Endpoint;
import rice.p2p.commonapi.rawserialization.InputBuffer;
import rice.p2p.past.*;
import rice.p2p.util.rawserialization.JavaDeserializer;

public class JavaPastContentHandleDeserializer implements
    PastContentHandleDeserializer {

  public PastContentHandle deserializePastContentHandle(InputBuffer buf, Endpoint endpoint, short contentType) throws IOException {   
    switch(contentType) {
      case 0:
        byte[] array = new byte[buf.readInt()];
        buf.read(array);
        
        ObjectInputStream ois = new JavaDeserializer(new ByteArrayInputStream(array), endpoint);

        try {
          Object o = ois.readObject();
          PastContentHandle ret = (PastContentHandle)o;
          return ret;
        } catch (ClassNotFoundException e) {
          throw new RuntimeException("Unknown class type in message - cant deserialize.", e);
        }            
      case ContentHashPastContentHandle.TYPE:
        return new ContentHashPastContentHandle(buf, endpoint);
    }
    throw new IllegalArgumentException("contentType must be 0 was:"+contentType+" endpoint:"+endpoint); 
  }
}
