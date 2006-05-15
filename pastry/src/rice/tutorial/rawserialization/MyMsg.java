/*
 * Created on Feb 15, 2005
 */
package rice.tutorial.rawserialization;

import java.io.IOException;

import rice.p2p.commonapi.*;
import rice.p2p.commonapi.rawserialization.*;

/**
 * An example message.
 * 
 * @author Jeff Hoye
 */
public class MyMsg implements RawMessage {
  protected static final short TYPE = 1;
  
  /**
   * Where the Message came from.
   */
  Id from;
  /**
   * Where the Message is going.
   */
  Id to;
  
  /**
   * Constructor.
   */
  public MyMsg(Id from, Id to) {
    this.from = from;
    this.to = to;
  }
  
  public String toString() {
    return "MyMsg from "+from+" to "+to;
  }

  /**
   * Use low priority to prevent interference with overlay maintenance traffic.
   */
  public byte getPriority() {
    return Message.LOW_PRIORITY;
  }

  public short getType() {
    return TYPE;
  }

  public MyMsg(InputBuffer buf, Endpoint endpoint) throws IOException {
    from = endpoint.readId(buf, buf.readShort()); 
    to = endpoint.readId(buf, buf.readShort()); 
  }
  
  public void serialize(OutputBuffer buf) throws IOException {
    buf.writeShort(from.getType());
    from.serialize(buf);
    buf.writeShort(to.getType());
    to.serialize(buf);    
  }
}
