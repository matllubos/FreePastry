package org.mpisws.p2p.transport;

import java.util.Map;

import rice.p2p.commonapi.Cancellable;

/**
 * Can cancel the request to send the message.
 * 
 * @author Jeff Hoye
 *
 * @param <Identifier>
 * @param <MessageType>
 */
public interface MessageRequestHandle<Identifier, MessageType> extends Cancellable {
  public MessageType getMessage();
  public Identifier getIdentifier();
  public Map<String, Integer> getOptions();
}
