package org.mpisws.p2p.transport.peerreview.history;

import java.nio.ByteBuffer;

import rice.p2p.commonapi.rawserialization.InputBuffer;

public interface HashProvider {
  public Hash hash(long seq, short type, Hash nodeHash, Hash contentHash);
  public Hash hash(ByteBuffer ... hashMe);
  
  public Hash build(InputBuffer buf);
  public Hash build(byte[] hashBytes, int start, int length);

  int getSerizlizedSize(); // 20 by default

  public Hash getEmpty();

}
