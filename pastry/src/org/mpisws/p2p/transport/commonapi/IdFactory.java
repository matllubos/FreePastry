package org.mpisws.p2p.transport.commonapi;

import java.io.IOException;

import rice.p2p.commonapi.Id;
import rice.p2p.commonapi.rawserialization.InputBuffer;

public interface IdFactory {
  public Id build(InputBuffer buf) throws IOException;
}
