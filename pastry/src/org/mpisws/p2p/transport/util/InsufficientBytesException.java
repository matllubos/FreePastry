package org.mpisws.p2p.transport.util;

import java.io.IOException;

public class InsufficientBytesException extends IOException {
  private int needed;
  private int available;
  public InsufficientBytesException(int needed, int available) {
    super("Not enough bytes available.  Need "+needed+" have "+available);  
    this.needed = needed;
    this.available = available;
  }
  
  private int getBytesAvailable() {
    return available;
  }
  
  private int getBytesNeeded() {
    return needed; 
  }
  
}
