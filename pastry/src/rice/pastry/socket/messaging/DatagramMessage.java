package rice.pastry.socket.messaging;

import java.io.*;
import java.net.*;

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
public abstract class DatagramMessage extends SocketMessage {
  
  protected long start;
  
  protected SourceRoute outbound;
  
  protected SourceRoute inbound;

  transient Environment environment;
  
  /**
   * Constructor
   */
  public DatagramMessage(SourceRoute outbound, SourceRoute inbound, Environment env) {
    this.outbound = outbound;
    this.inbound = inbound;
    this.environment = env;
  }
  
  public long getStartTime() {
    return start;
  }
  
  public SourceRoute getOutboundPath() {
    return outbound;
  }
  
  public SourceRoute getInboundPath() {
    return inbound;
  }
  
  private void writeObject(ObjectOutputStream oos) throws IOException {
    if (start == 0)
      start = environment.getTimeSource().currentTimeMillis();
    oos.defaultWriteObject();
  }
}
