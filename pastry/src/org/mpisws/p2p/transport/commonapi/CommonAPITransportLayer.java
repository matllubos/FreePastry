package org.mpisws.p2p.transport.commonapi;

import org.mpisws.p2p.transport.TransportLayer;
import org.mpisws.p2p.transport.liveness.LivenessProvider;
import org.mpisws.p2p.transport.proximity.ProximityProvider;

import rice.p2p.commonapi.rawserialization.RawMessage;

/**
 * TransportLayer for the rice.p2p.commonapi.
 * 
 * Uses NodeHandle as Identifier
 * Serializes RawMessage
 * 
 * TODO: Rename to CommonAPITransportLayer
 * 
 * @author Jeff Hoye
 *
 */
public interface CommonAPITransportLayer<Identifier> extends 
  TransportLayer<TransportLayerNodeHandle<Identifier>, RawMessage>, 
  LivenessProvider<TransportLayerNodeHandle<Identifier>>, 
  ProximityProvider<TransportLayerNodeHandle<Identifier>> {

}
