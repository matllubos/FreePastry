package org.mpisws.p2p.transport.peerreview.history.stub;

import org.mpisws.p2p.transport.peerreview.history.Hash;

import rice.p2p.commonapi.rawserialization.OutputBuffer;

public class EmptyHash implements Hash {

  public void serialize(OutputBuffer buf) {
    // do nothing
  }

}
