
package rice.p2p.past.gc.messaging;

import java.io.IOException;

import rice.*;
import rice.environment.Environment;
import rice.p2p.commonapi.*;
import rice.p2p.commonapi.rawserialization.*;
import rice.p2p.past.*;
import rice.p2p.past.messaging.*;

/**
 * @(#) GCCollectMessage.java
 *
 * This class represents a message which tells GC Past that it's time to
 * delete all the expired objects in the local store.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */
public class GCCollectMessage extends PastMessage {
  public static final short TYPE = 8;

  /**
   * Constructor
   *
   * @param id The location to be stored
   * @param source The source address
   * @param dest The destination address
   */
  public GCCollectMessage(int id, NodeHandle source, Id dest) {
    super(id, source, dest);
  }

  /**
   * Method by which this message is supposed to return it's response -
   * in this case, it lets the continuation know that a the message was
   * lost via the receiveException method.
   *
   * @param c The continuation to return the reponse to.
   */
  public void returnResponse(Continuation c, Environment env, String instance) {
    c.receiveException(new PastException("Should not be called!"));
  }
  
  /**
  * Returns a string representation of this message
   *
   * @return A string representing this message
   */
  public String toString() {
    return "[GCCollectMessage]";
  }
  
  public short getType() {
    return TYPE; 
  }
  
  public void serialize(OutputBuffer buf) throws IOException {
    throw new RuntimeException("serialize() not supported in MessageLostMessage"); 
  }
}

