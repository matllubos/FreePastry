package org.mpisws.p2p.transport;

/**
 * Java's CCE doesn't have proper constructors.  Silly java.
 * 
 * @author Jeff Hoye
 *
 */
public class ClosedChannelException extends
    java.nio.channels.ClosedChannelException {
  String reason;
  
  public ClosedChannelException(String reason) {
    this.reason = reason;
  }
  
  @Override
  public String getMessage() {
    return reason;
  }
}
