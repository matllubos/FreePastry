package org.mpisws.p2p.transport.peerreview.history.stub;

import org.mpisws.p2p.transport.peerreview.history.Hash;

import rice.p2p.commonapi.rawserialization.OutputBuffer;

public class NullHash implements Hash {
  private static final byte[] NOTHING = new byte[0];
  public void serialize(OutputBuffer buf) {
    // do nothing
  }
  public byte[] getBytes() {
    return NOTHING;
  }

}
