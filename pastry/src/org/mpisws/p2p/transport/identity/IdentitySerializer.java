package org.mpisws.p2p.transport.identity;

import java.io.IOException;
import java.nio.ByteBuffer;

public interface IdentitySerializer<Identifier> {
  public byte[] serialize(Identifier i) throws IOException;

  public Identifier deserialize(ByteBuffer m) throws IOException;
}
