package org.mpisws.p2p.transport.liveness;

import rice.Continuation;

public interface LivenessStrategy<Identifier, MessageType> {
  /**
   * Notify the continuation when the liveness check has completed.
   * 
   * @param i
   * @param c
   */
  void checkLiveness(Identifier i, Continuation<Identifier, Exception> c);

  /**
   * Return the current liveness of the address.
   * 
   * @param identity
   * @return
   */
  int getLiveness(Identifier identity);
  
  void messageReceived(Identifier i, MessageType m);

  void socketConnected(Identifier i);
  void socketAccepted(Identifier i);
  
  void socketFailed(Identifier i);
  

}
