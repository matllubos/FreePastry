/*
 * Created on Feb 16, 2006
 */
package rice.p2p.commonapi.rawserialization;

import java.io.IOException;

import rice.p2p.commonapi.*;

/**
 * Because Pastry/Transport layer cannot know about all messge types, each app needs to 
 * provide a deserializer.  Default, there is a Java Serializer 
 */   
public interface MessageDeserializer {
  /**
   * RawMessage ret = super.deserialize();
   * if (ret != null) return ret;
   *
   * Endpoint endpoint;
   * switch(type) {
   *    case 1:
   *      return new MyMessage(buf, endpoint);
   * }
   */
  Message deserialize(InputBuffer buf, short type, int priority, NodeHandle sender) throws IOException;
}
