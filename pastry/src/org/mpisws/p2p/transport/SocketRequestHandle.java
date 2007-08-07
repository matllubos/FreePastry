package org.mpisws.p2p.transport;

import java.util.Map;

import rice.p2p.commonapi.Cancellable;

/**
 * Can cancel the request to open the socket.
 * 
 * @author Jeff Hoye
 *
 * @param <Identifier>
 */
public interface SocketRequestHandle<Identifier> extends Cancellable {
  public Identifier getIdentifier();
  public Map<String, Integer> getOptions();
}
