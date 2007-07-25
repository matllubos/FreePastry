package org.mpisws.p2p.transport.peerreview;

public interface SecureHistory {
  public int getNumEntries();
  public long getBaseSeq();
  public long getLastSeq();
}
