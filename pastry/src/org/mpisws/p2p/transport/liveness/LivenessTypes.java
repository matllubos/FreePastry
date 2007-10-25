package org.mpisws.p2p.transport.liveness;

public interface LivenessTypes {
  public static final int LIVENESS_ALIVE = 1;
  public static final int LIVENESS_SUSPECTED = 2;
  public static final int LIVENESS_DEAD = 3;
  public static final int LIVENESS_DEAD_FOREVER = 4;
}
