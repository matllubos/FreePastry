package org.mpisws.p2p.transport.rendezvous;

import rice.Continuation;
import rice.p2p.commonapi.Cancellable;

/**
 * Uses a 3rd party channel to request a node to connect to a dest.
 * 
 * @author Jeff Hoye
 *
 */
public interface RendezvousStrategy<Identifier> {
  public static int SUCCESS = 1;

  /**
   * Calls ChannelOpener.openChannel(dest, credentials) on target
   * 
   * Possible exceptions to deliverResultToMe:
   *   NodeIsFaultyException if target is faulty
   *   UnableToConnectException if dest is faulty
   *   
   * @param target call ChannelOpener.openChannel() on this Identifier
   * @param rendezvous pass this to ChannelOpener.openChannel(), it's who the ChannelOpener will connect to
   * @param credentials this is also passed to ChannelOpener.openChannel()
   * @param deliverResultToMe notify me when success/failure
   * @return a way to cancel the request
   */
  public Cancellable openChannel(Identifier target, Identifier rendezvous, byte[] credentials, Continuation<Integer, Exception> deliverResultToMe);
}
