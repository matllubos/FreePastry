
package rice.pastry.socket.messaging;

import java.net.*;
import java.io.*;

import rice.environment.Environment;
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
  
  protected EpochInetSocketAddress incorrect;
  protected EpochInetSocketAddress correct;
  
  /**
  * Constructor
   */
  public WrongEpochMessage(SourceRoute outbound, SourceRoute inbound, EpochInetSocketAddress incorrect, EpochInetSocketAddress correct, long start) {
    super(outbound, inbound, start);
    
    this.incorrect = incorrect;
    this.correct = correct;
  }
  
  public EpochInetSocketAddress getIncorrect() {
    return incorrect;
  }
  
  public EpochInetSocketAddress getCorrect() {
    return correct;
  }

  public String toString() {
    return "PingResponseMessage";
  }
}
