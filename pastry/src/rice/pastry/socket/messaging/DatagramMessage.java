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
  
  static final long serialVersionUID = 5928529749829923541L;
  
  protected long start;
  
  protected SourceRoute outbound;
  
  protected SourceRoute inbound;

  /**
   * Constructor
   */
  public DatagramMessage(SourceRoute outbound, SourceRoute inbound, long start) {
    this.outbound = outbound;
    this.inbound = inbound;
    this.start = start;
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
}
