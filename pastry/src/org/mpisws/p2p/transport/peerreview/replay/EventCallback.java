package org.mpisws.p2p.transport.peerreview.replay;

import java.nio.ByteBuffer;

import rice.p2p.commonapi.rawserialization.InputBuffer;

public interface EventCallback {
  void replayEvent(short type, InputBuffer entry);
}
