package org.mpisws.p2p.transport.peerreview.replay;

import java.nio.ByteBuffer;

public interface EventCallback {
  void replayEvent(short type, ByteBuffer ... entry);
}
