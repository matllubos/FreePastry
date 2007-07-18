package org.mpisws.p2p.transport.liveness;

import java.util.Map;

public interface Pinger<Identifier> {
  /**
   * @param i the identifier that responded 
   * @param options transport layer dependent way to send the ping (udp/tcp etc)
   * @return true If the ping will occur.  (Maybe it won't due to bandwidth concerns.)
   */
  public boolean ping(Identifier i, Map<String, Integer> options);

  public void addPingListener(PingListener<Identifier> name);
  public boolean removePingListener(PingListener<Identifier> name);
}
