package org.mpisws.p2p.transport.sourceroute;

import java.io.IOException;
import java.util.List;

import org.mpisws.p2p.transport.multiaddress.MultiInetSocketAddress;

import rice.p2p.commonapi.rawserialization.InputBuffer;

public interface SourceRouteFactory<Identifier> {
  public SourceRoute<Identifier> getSourceRoute(List<Identifier> route);
  public SourceRoute<Identifier> reverse(SourceRoute<Identifier> route);
  public SourceRoute<Identifier> build(InputBuffer buf) throws IOException;
  public SourceRoute<Identifier> getSourceRoute(Identifier local, Identifier dest);  
  public SourceRoute<Identifier> getSourceRoute(Identifier local);  
}
