package org.mpisws.p2p.transport.commonapi;

import java.io.IOException;
import java.nio.ByteBuffer;

import rice.p2p.commonapi.Id;
import rice.p2p.commonapi.NodeHandle;
import rice.p2p.commonapi.rawserialization.InputBuffer;

public interface NodeHandleFactory<Identifier> {
  public TransportLayerNodeHandle<Identifier> lookupNodeHandle(Identifier i);
  public TransportLayerNodeHandle<Identifier> getNodeHandle(Identifier i, long epoch, Id id);
  public TransportLayerNodeHandle<Identifier> readNodeHandle(InputBuffer buf) throws IOException;
}
