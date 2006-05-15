package rice.p2p.glacier.v2.messaging;

import java.io.IOException;

import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.commonapi.rawserialization.*;
import rice.p2p.glacier.*;

public class GlacierTimeoutMessage extends GlacierMessage {
  public static final short TYPE = 15;


  /**
   * Constructor which takes a unique integer Id and the local id
   *
   * @param uid The unique id
   * @param local The local nodehandle
   */
  public GlacierTimeoutMessage(int uid, NodeHandle local) {
    super(uid, local, local.getId(), false, (char)0);
  }

  /**
  * Returns a string representation of this message
   *
   * @return A string representing this message
   */
  public String toString() {
    return "[GlacierTimeoutMessage "+getUID()+"]";
  }
  
  /***************** Raw Serialization ***************************************/
  public short getType() {
    return TYPE; 
  }
  
  public void serialize(OutputBuffer buf) throws IOException {
    throw new RuntimeException("serialize() not supported in GlacierTimeoutMessage"); 
  }
}

