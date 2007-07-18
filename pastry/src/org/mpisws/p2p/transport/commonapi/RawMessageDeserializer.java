package org.mpisws.p2p.transport.commonapi;

import java.io.IOException;
import java.nio.ByteBuffer;

import rice.p2p.commonapi.rawserialization.InputBuffer;
import rice.p2p.commonapi.rawserialization.OutputBuffer;
import rice.p2p.commonapi.rawserialization.RawMessage;

public interface RawMessageDeserializer {
  public RawMessage deserialize(InputBuffer b) throws IOException;

  public void serialize(RawMessage m, OutputBuffer b) throws IOException;
}
