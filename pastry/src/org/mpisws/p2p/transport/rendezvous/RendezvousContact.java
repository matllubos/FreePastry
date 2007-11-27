package org.mpisws.p2p.transport.rendezvous;

public interface RendezvousContact {
  /**
   * @return true if an internet routable IP; false if NATted and no port forwarding, or other type FireWall.
   */
  public boolean canContactDirect();
  
  /**
   * return True if isAlive() and canContactDirect() or we already have a direct connection on the third party channel.  False otherwise.
   * 
   * Note that this is true if we have a primary socket opened in PriorityTL
   * 
   * @return
   */
  public boolean isConnected();
}
