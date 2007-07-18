package org.mpisws.p2p.transport;

import java.io.IOException;

public interface SocketCallback<Identifier> {
  public void receiveResult(SocketRequestHandle<Identifier> cancellable, P2PSocket<Identifier> sock);
  public void receiveException(SocketRequestHandle<Identifier> s, IOException ex);
}
