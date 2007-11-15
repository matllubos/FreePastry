package org.mpisws.p2p.transport.rendezvous;

import java.util.Map;

public interface RendezvousGenerationStrategy<Identifier> {
  public Identifier getRendezvousPoint(Identifier dest, Map<String, Object> options);
}
