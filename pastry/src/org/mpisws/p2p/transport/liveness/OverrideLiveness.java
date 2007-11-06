package org.mpisws.p2p.transport.liveness;

import java.util.Map;

public interface OverrideLiveness<Identifier> {
  void setLiveness(Identifier i, int liveness, Map<String, Object> options);
}
