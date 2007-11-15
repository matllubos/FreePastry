package org.mpisws.p2p.transport.rendezvous;

import rice.Continuation;
import rice.p2p.commonapi.Cancellable;

public interface ChannelOpener<Identifier> {
  public static int SUCCESS = 1;

  /**
   * Open a socket to the dest, then after writing credentials, call notify the higher layer: incomingSocket()
   * 
   * @param dest open a channel to here, and then write credentials
   * @param connectionType TCP, UDP, TCP_AND_UDP, STUN 
   * @param credentials
   * @param deliverResultToMe
   * @return
   */
  public Cancellable openChannel(Identifier dest, 
      byte cnnectionType, 
      byte[] credentials, 
      Continuation<Integer, Exception> deliverResultToMe);
}
