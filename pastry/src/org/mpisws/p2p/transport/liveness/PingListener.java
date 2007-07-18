package org.mpisws.p2p.transport.liveness;

import java.util.Map;

/**
 * Called when a ping is received.
 * 
 * @author Jeff Hoye
 *
 * @param <Identifier>
 */
public interface PingListener<Identifier> {
  /**
   * Pong received.
   * 
   * @param i Where the ping was from
   * @param rtt the RTT
   * @param options how the ping was sent (source route/udp etc)
   */
  public void pingResponse(Identifier i, int rtt, Map<String, Integer> options);
  
  /**
   * Called when we receive a ping (not a pong)
   * @param i
   * @param options
   */
  public void pingReceived(Identifier i, Map<String, Integer> options);
}
