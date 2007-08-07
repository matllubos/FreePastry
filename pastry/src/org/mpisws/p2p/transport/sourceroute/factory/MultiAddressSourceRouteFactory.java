package org.mpisws.p2p.transport.sourceroute.factory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.mpisws.p2p.transport.multiaddress.MultiInetSocketAddress;
import org.mpisws.p2p.transport.sourceroute.SourceRoute;
import org.mpisws.p2p.transport.sourceroute.SourceRouteFactory;

import rice.p2p.commonapi.rawserialization.InputBuffer;

public class MultiAddressSourceRouteFactory implements SourceRouteFactory<MultiInetSocketAddress> {

  public SourceRoute<MultiInetSocketAddress> build(InputBuffer buf) throws IOException {
    byte numInPath = buf.readByte();
    ArrayList<MultiInetSocketAddress> path = new ArrayList<MultiInetSocketAddress>(numInPath);
    for (int i = 0; i < numInPath; i++) {
      path.add(MultiInetSocketAddress.build(buf));
    }    
    return new MultiAddressSourceRoute(path);
  }

  public SourceRoute<MultiInetSocketAddress> getSourceRoute(List<MultiInetSocketAddress> route) {
    return new MultiAddressSourceRoute(route);
  }

  public SourceRoute<MultiInetSocketAddress> reverse(SourceRoute<MultiInetSocketAddress> route) {
    MultiAddressSourceRoute temp = (MultiAddressSourceRoute)route;
    ArrayList<MultiInetSocketAddress> result = new ArrayList<MultiInetSocketAddress>(temp.getPath());
    
    Collections.reverse(result);
    
    return new MultiAddressSourceRoute(result);
  }

  public SourceRoute<MultiInetSocketAddress> getSourceRoute(
      MultiInetSocketAddress local, MultiInetSocketAddress dest) {
    return new MultiAddressSourceRoute(local, dest);
  }

  public SourceRoute<MultiInetSocketAddress> getSourceRoute(MultiInetSocketAddress local) {
    return new MultiAddressSourceRoute(local);
  }
}
