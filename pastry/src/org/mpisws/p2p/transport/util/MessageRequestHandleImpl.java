package org.mpisws.p2p.transport.util;

import java.util.Map;

import org.mpisws.p2p.transport.MessageRequestHandle;

import rice.p2p.commonapi.Cancellable;

public class MessageRequestHandleImpl<Identifier, MessageType> implements MessageRequestHandle<Identifier, MessageType> {
  Cancellable subCancellable;
  Identifier identifier;
  MessageType msg;
  Map<String, Integer> options;
  
  public MessageRequestHandleImpl(Identifier i, MessageType m, Map<String, Integer> options) {
    this.identifier = i;
    this.msg = m;
    this.options = options;
  }

  public Identifier getIdentifier() {
    return identifier;
  }

  public MessageType getMessage() {
    return msg;
  }

  public Map<String, Integer> getOptions() {
    return options;
  }

  public boolean cancel() {
    return subCancellable.cancel();
  }

  public void setSubCancellable(Cancellable cancellable) {
    this.subCancellable = cancellable;
  }
  
  public Cancellable getSubCancellable() {
    return subCancellable;
  }
}
