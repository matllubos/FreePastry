package org.mpisws.p2p.transport.util;

import java.util.Map;

import org.mpisws.p2p.transport.SocketRequestHandle;

import rice.p2p.commonapi.Cancellable;

public class SocketRequestHandleImpl<Identifier> implements SocketRequestHandle<Identifier> {
  Identifier identifier;
  Map<String, Integer> options;
  Cancellable subCancellable;
  
  public SocketRequestHandleImpl(Identifier i, Map<String, Integer> options) {
    this.identifier = i;
    this.options = options;
  }

  public Identifier getIdentifier() {
    return identifier;
  }

  public Map<String, Integer> getOptions() {
    return options;
  }

  public boolean cancel() {
    return subCancellable.cancel();
  }

  public void setSubCancellable(Cancellable sub) {
    this.subCancellable = sub;
  }

  public Cancellable getSubCancellable() {
    return subCancellable;
  }

  @Override
  public String toString() {
    return "SRHi{"+identifier+","+options+"}";
  }

}
