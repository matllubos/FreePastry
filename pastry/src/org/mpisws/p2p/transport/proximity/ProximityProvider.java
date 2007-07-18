package org.mpisws.p2p.transport.proximity;

public interface ProximityProvider<Identifier> {
  public int proximity(Identifier i);

  public void addProximityListener(ProximityListener<Identifier> listener);
  public boolean removeProximityListener(ProximityListener<Identifier> listener);
}
