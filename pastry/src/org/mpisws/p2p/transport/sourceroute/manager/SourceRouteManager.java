package org.mpisws.p2p.transport.sourceroute.manager;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.mpisws.p2p.transport.TransportLayer;
import org.mpisws.p2p.transport.liveness.LivenessProvider;
import org.mpisws.p2p.transport.multiaddress.MultiInetSocketAddress;
import org.mpisws.p2p.transport.proximity.ProximityProvider;

/**
 * The purpose of this class is to hide the detail of sourcerouting.  It adapts a 
 * TransportLayer<SourceRoute, Buffer> => TransportLayer<Identifier, Buffer>
 * 
 * @author Jeff Hoye
 *
 */
public interface SourceRouteManager<Identifier> extends 
  TransportLayer<Identifier, ByteBuffer>, 
  LivenessProvider<Identifier>, 
  ProximityProvider<Identifier> {
}
