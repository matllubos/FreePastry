package org.mpisws.p2p.transport.identity;

public interface SanityChecker<UpperIdentifier, MiddleIdentifier> {
  /**
   * @return true if the UpperIdentifier matches the MiddleIdentifier
   */
  boolean isSane(UpperIdentifier upper, MiddleIdentifier middle);
}
