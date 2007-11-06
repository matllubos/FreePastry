package org.mpisws.p2p.transport.sourceroute.nat;

public interface RendezvousStrategy<Identifier> {
  public Identifier getRendezvous(Identifier destination);
}
