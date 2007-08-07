/**
 * 
 */
package org.mpisws.p2p.transport.liveness;

import java.util.Map;

/**
 * Notified of liveness changes.
 * 
 * @author Jeff Hoye
 *
 */
public interface LivenessListener<Identifier> {
  public static final int LIVENESS_ALIVE = 1;
  public static final int LIVENESS_SUSPECTED = 2;
  public static final int LIVENESS_DEAD = 3;
  public static final int LIVENESS_DEAD_FOREVER = 4;

  /**
   * Called when the liveness changes.
   * 
   * @param i
   * @param val
   */
  public void livenessChanged(Identifier i, int val, Map<String, Integer> options);
}
