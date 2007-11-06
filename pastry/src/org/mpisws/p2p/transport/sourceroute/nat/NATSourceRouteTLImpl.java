package org.mpisws.p2p.transport.sourceroute.nat;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;

import org.mpisws.p2p.transport.ErrorHandler;
import org.mpisws.p2p.transport.MessageCallback;
import org.mpisws.p2p.transport.MessageRequestHandle;
import org.mpisws.p2p.transport.P2PSocket;
import org.mpisws.p2p.transport.SocketCallback;
import org.mpisws.p2p.transport.SocketRequestHandle;
import org.mpisws.p2p.transport.TransportLayer;
import org.mpisws.p2p.transport.sourceroute.SourceRoute;
import org.mpisws.p2p.transport.sourceroute.SourceRouteFactory;
import org.mpisws.p2p.transport.sourceroute.SourceRouteTransportLayerImpl;
import org.mpisws.p2p.transport.util.SocketRequestHandleImpl;

import rice.environment.Environment;

/**
 * Extends SourceRouteImpl with NAT awareness.
 * 
 * If trying to open a connection to a NATted node, requests a Rendezvous from the strategy.
 * 
 * @author Jeff Hoye
 *
 * @param <Identifier>
 */
public class NATSourceRouteTLImpl<Identifier> extends SourceRouteTransportLayerImpl<Identifier> {
  public static final String NATTED = "NATTED";
  public static final int TRUE = 1;
  public static final int FALSE = 0;
  
  public static final byte NORMAL_SOCKET = 0;
  public static final byte REQUEST_SOCKET = 1;
  public static final byte RESPONSE_SOCKET = 2;
  
  RendezvousStrategy<Identifier> rendezvousStrategy;
  
  public NATSourceRouteTLImpl(SourceRouteFactory<Identifier> srFactory, TransportLayer<Identifier, ByteBuffer> etl, RendezvousStrategy<Identifier> strategy, Environment env, ErrorHandler<SourceRoute<Identifier>> errorHandler) {
    super(srFactory, etl, env, errorHandler);
    this.rendezvousStrategy = strategy;
  }

  @Override
  public SocketRequestHandle<SourceRoute<Identifier>> openSocket(SourceRoute<Identifier> i, SocketCallback<SourceRoute<Identifier>> deliverSocketToMe, Map<String, Object> options) {
    if (options != null && options.containsKey(NATTED) && options.get(NATTED).equals(TRUE)) {
      rendezvousStrategy.getRendezvous(i.getLastHop()); // what is the best destination to deliver here?
      
      return null; // TODO: need to return a proper handle
    }
    
    // else, do the normal thing, but we need to write the 
    return super.openSocket(i, deliverSocketToMe, options);
  }
  
  @Override
  protected void incomingSocketHelper(P2PSocket<Identifier> socket, SourceRoute<Identifier> sr) throws IOException {
    // TODO read byte for XXX_SOCKET
    super.incomingSocketHelper(socket, sr);
  }

  @Override
  protected void openSocketHelper(SocketCallback<SourceRoute<Identifier>> deliverSocketToMe, SocketRequestHandleImpl<SourceRoute<Identifier>> handle, P2PSocket<Identifier> socket, SourceRoute<Identifier> i) {
    // TODO write byte of XXX_SOCKET
    super.openSocketHelper(deliverSocketToMe, handle, socket, i);
  }
}
