package org.mpisws.p2p.transport.identity;

import java.nio.ByteBuffer;

import org.mpisws.p2p.transport.TransportLayer;
import org.mpisws.p2p.transport.liveness.LivenessProvider;
import org.mpisws.p2p.transport.proximity.ProximityProvider;

import rice.p2p.commonapi.rawserialization.RawMessage;

/**
 * When the LowerIdentity reports that a destination has changed, this layer
 * cancels all pending messages.  And reports "NodeDeadForever" as a liveness change for the old identiy.
 * 
 * @author Jeff Hoye
 *
 */
public interface UpperIdentity<Identifier, MessageType> extends 
  TransportLayer<Identifier, MessageType>, 
  LivenessProvider<Identifier>, 
  ProximityProvider<Identifier> {
  
}
