package org.mpisws.p2p.transport.peerreview.replay;

import java.io.IOException;
import java.nio.ByteBuffer;

import rice.p2p.commonapi.rawserialization.InputBuffer;
import rice.p2p.commonapi.rawserialization.OutputBuffer;

public interface IdentifierSerializer<Identifier> {
  public void serialize(Identifier i, OutputBuffer buf) throws IOException;
  public ByteBuffer serialize(Identifier i);

  public Identifier deserialize(InputBuffer buf) throws IOException;
}
