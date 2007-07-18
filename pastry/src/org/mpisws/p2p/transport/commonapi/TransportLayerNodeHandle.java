package org.mpisws.p2p.transport.commonapi;

import java.io.IOException;

import rice.p2p.commonapi.Id;
import rice.p2p.commonapi.rawserialization.OutputBuffer;

/**
 * Parallel interface to the CommonAPI NodeHandle, because it is an abstract object to gain the 
 * observer pattern.
 * 
 * @author Jeff Hoye
 *
 * @param <Identifier> the underlieing layer
 */
public interface TransportLayerNodeHandle<Identifier> {
  public Identifier getAddress();
  public Id getId();
  public void serialize(OutputBuffer sob) throws IOException;
  public long getEpoch();
}
