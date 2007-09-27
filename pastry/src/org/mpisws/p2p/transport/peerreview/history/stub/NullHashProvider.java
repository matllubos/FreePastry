package org.mpisws.p2p.transport.peerreview.history.stub;

import java.nio.ByteBuffer;

import org.mpisws.p2p.transport.peerreview.history.Hash;
import org.mpisws.p2p.transport.peerreview.history.HashProvider;

import rice.p2p.commonapi.rawserialization.InputBuffer;

public class NullHashProvider implements HashProvider {

  public static final EmptyHash EMPTY_HASH = new EmptyHash();
  
  public Hash hash(long seq, short type, Hash nodeHash, Hash contentHash) {
    return EMPTY_HASH;
  }

  public Hash hash(ByteBuffer... hashMe) {
    return EMPTY_HASH;
  }

  public Hash build(InputBuffer buf) {
    return EMPTY_HASH;
  }

  public Hash getEmpty() {
    return EMPTY_HASH;
  }

  public int getSerizlizedSize() {
    return 0;
  }

}
