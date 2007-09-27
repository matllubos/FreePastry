package org.mpisws.p2p.transport.peerreview.replay;

import java.nio.ByteBuffer;

public interface IdentifierSerializer<Identifier> {
  public ByteBuffer serialize(Identifier i);
}
