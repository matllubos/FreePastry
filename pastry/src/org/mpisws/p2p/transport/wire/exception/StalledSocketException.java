package org.mpisws.p2p.transport.wire.exception;

import java.io.IOException;
import java.net.InetSocketAddress;

public class StalledSocketException extends IOException {

  protected Object addr;
  
  public StalledSocketException(Object addr, String s) {
    super(s);
    this.addr = addr;
  }
  
  public StalledSocketException(InetSocketAddress addr) {
    super(addr.toString());
    this.addr = addr;
  }
  
  public Object getIdentifier() {
    return addr;
  }
}
