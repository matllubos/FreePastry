package org.mpisws.p2p.transport.sourceroute;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.mpisws.p2p.transport.TransportLayer;

public interface SourceRouteTransportLayer<Identifier> extends TransportLayer<SourceRoute<Identifier>, ByteBuffer> {
  public static final String OPTION_SOURCE_ROUTE = "source_route";
  public static final int DONT_SOURCE_ROUTE = 0; // direct only
  public static final int ALLOW_SOURCE_ROUTE = 1;
  
  void addSourceRouteTap(SourceRouteTap tap);
  boolean removeSourceRouteTap(SourceRouteTap tap);
}
