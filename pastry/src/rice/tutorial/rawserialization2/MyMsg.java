/*
 * Created on Feb 15, 2005
 */
package rice.tutorial.rawserialization2;

import java.io.IOException;

import rice.p2p.commonapi.*;
import rice.p2p.commonapi.rawserialization.*;
import rice.p2p.past.messaging.LookupMessage;
import rice.p2p.util.rawserialization.JavaSerializer;

/**
 * An example message.
 * 
 * @author Jeff Hoye
 */
public class MyMsg extends LookupMessage {
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
  public MyMsg(NodeHandle from, Id to) {
    super(0,from.getId(),from,to);
    this.from = from.getId();
    this.to = to;
  }
  
  public String toString() {
    return "MyMsg from "+from+" to "+to;
  }

  /**
   * Use low priority to prevent interference with overlay maintenance traffic.
   */
  public int getPriority() {
    return Message.LOW_PRIORITY;
  }

  public short getType() {
    return TYPE;
  }

  public void serialize(OutputBuffer buf) throws IOException {
    JavaSerializer.serialize(this, buf);
  }
}
