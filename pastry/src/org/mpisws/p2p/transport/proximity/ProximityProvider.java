package org.mpisws.p2p.transport.proximity;

public interface ProximityProvider<Identifier> {
  // the default distance, which is used before a ping
  public static final int DEFAULT_PROXIMITY = 60*60*1000; // 1 hour

  public int proximity(Identifier i);

  public void addProximityListener(ProximityListener<Identifier> listener);
  public boolean removeProximityListener(ProximityListener<Identifier> listener);
}
