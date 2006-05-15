
package rice.pastry.socket.messaging;

import java.net.*;
import java.io.*;

import rice.environment.Environment;
import rice.p2p.commonapi.*;
import rice.p2p.commonapi.rawserialization.*;
import rice.pastry.socket.*;
import rice.pastry.*;

/**
 * Class which represents a "ping" message sent through the
 * socket pastry system.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */
public class WrongEpochMessage extends DatagramMessage {
  
  static final long serialVersionUID = 2838948342952784682L;

  public static final short TYPE = 14;
  
  protected EpochInetSocketAddress incorrect;
  protected EpochInetSocketAddress correct;
  
  /**
  * Constructor
   */
  public WrongEpochMessage(/*SourceRoute outbound, SourceRoute inbound, */EpochInetSocketAddress incorrect, EpochInetSocketAddress correct, long start) {
    super(/*outbound, inbound,*/ start);
    
    this.incorrect = incorrect;
    this.correct = correct;
  }
  
  public WrongEpochMessage(InputBuffer buf) throws IOException {
    super(buf);
    incorrect = EpochInetSocketAddress.build(buf);
    correct = EpochInetSocketAddress.build(buf);
  }

  public EpochInetSocketAddress getIncorrect() {
    return incorrect;
  }
  
  public EpochInetSocketAddress getCorrect() {
    return correct;
  }

  public String toString() {
    return "WrongEpochMessage";
  }

  public short getType() {
    return TYPE;        
  }

  public void serialize(OutputBuffer buf) throws IOException {
    super.serialize(buf);
    incorrect.serialize(buf);
    correct.serialize(buf);
  }
}
