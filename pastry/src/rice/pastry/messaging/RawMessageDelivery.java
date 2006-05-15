/*
 * Created on Feb 22, 2006
 */
package rice.pastry.messaging;

import java.io.IOException;

import rice.p2p.commonapi.rawserialization.MessageDeserializer;

/**
 * 
 * Represents a message from the wire that hasn't yet been deserialized.
 * This gets passed to the application to be deserialized.
 * 
 * @author Jeff Hoye
 */
public interface RawMessageDelivery {
  public int getAddress();
  public Message deserialize(MessageDeserializer md) throws IOException;
}
