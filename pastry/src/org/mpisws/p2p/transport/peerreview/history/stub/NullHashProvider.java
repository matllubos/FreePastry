package org.mpisws.p2p.transport.peerreview.history.stub;

import java.nio.ByteBuffer;

import org.mpisws.p2p.transport.peerreview.history.Hash;
import org.mpisws.p2p.transport.peerreview.history.HashProvider;

import rice.p2p.commonapi.rawserialization.InputBuffer;

public class NullHashProvider implements HashProvider {

  public static final NullHash EMPTY_HASH = new NullHash();
  
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

  public Hash build(byte[] hashBytes, int start, int length) {
    if (length > 0) throw new IllegalArgumentException("Length must equal 0");
    return EMPTY_HASH;
  }

}
