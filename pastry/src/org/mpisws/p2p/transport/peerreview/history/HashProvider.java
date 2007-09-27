package org.mpisws.p2p.transport.peerreview.history;

import java.nio.ByteBuffer;

import rice.p2p.commonapi.rawserialization.InputBuffer;

public interface HashProvider {
  public Hash hash(long seq, short type, Hash nodeHash, Hash contentHash);
  public Hash hash(ByteBuffer ... hashMe);
  
  Hash build(InputBuffer buf);

  int getSerizlizedSize(); // 20 by default

  Hash getEmpty();

}
