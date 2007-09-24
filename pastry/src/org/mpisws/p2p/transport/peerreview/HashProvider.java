package org.mpisws.p2p.transport.peerreview;

public interface HashProvider {
  public Hash hash(long seq, short type, Hash nodeHash, Hash contentHash);
  public Hash hash(byte[] ... hashMe);
}
