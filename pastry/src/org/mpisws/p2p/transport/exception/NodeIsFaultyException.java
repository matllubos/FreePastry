package org.mpisws.p2p.transport.exception;

import java.io.IOException;

public class NodeIsFaultyException extends IOException {
  Object identifier;
  Object message;
  Throwable cause;

  public NodeIsFaultyException(Object identifier, Object message) {
    super("Node "+identifier+" is faulty"+(message == null ? "" : ", couldn't deliver message "+message));
    this.identifier = identifier;
    this.message = message;
  }

  public NodeIsFaultyException(Object identifier, Object message, Throwable cause) {
    this(identifier, message);
    this.cause = cause;
  }
  
  public NodeIsFaultyException(Object identifier) {
    this(identifier, null);
  }
  
  public Object getIdentifier() {
    return identifier;
  }
  
  public Object getAttemptedMessage() {
    return message;
  }  
  
  public Throwable getCause() {
    return cause;
  }
}
