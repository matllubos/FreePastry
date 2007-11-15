package org.mpisws.p2p.transport.rendezvous;

public interface RendezvousContact {
  /**
   * @return true if an internet routable IP; false if NATted and no port forwarding, or other type FireWall.
   */
  public boolean canContactDirect();  
}
