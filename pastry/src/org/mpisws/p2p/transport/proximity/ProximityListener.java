package org.mpisws.p2p.transport.proximity;

import java.util.Map;

public interface ProximityListener<Identifier> {
  public void proximityChanged(Identifier i, int newProximity, Map<String, Integer> options);
}
