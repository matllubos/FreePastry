package org.mpisws.p2p.transport.sourceroute.factory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.mpisws.p2p.transport.sourceroute.SourceRoute;
import org.mpisws.p2p.transport.sourceroute.SourceRouteFactory;

import rice.p2p.commonapi.rawserialization.InputBuffer;

public class WireSourceRouteFactory implements SourceRouteFactory<InetSocketAddress> {

  public SourceRoute<InetSocketAddress> build(InputBuffer buf) throws IOException {
    byte numInPath = buf.readByte();
    ArrayList<InetSocketAddress> path = new ArrayList<InetSocketAddress>(numInPath);
    for (int i = 0; i < numInPath; i++) {
      byte[] addrBytes = new byte[4];
      buf.read(addrBytes);
      InetAddress addr = InetAddress.getByAddress(addrBytes);
      short port = buf.readShort();

      path.add(new InetSocketAddress(addr, 0xFFFF & port));
    }    
    return new WireSourceRoute(path);
  }

  public SourceRoute<InetSocketAddress> getSourceRoute(List<InetSocketAddress> route) {
    return new WireSourceRoute(route);
  }

  public SourceRoute<InetSocketAddress> reverse(SourceRoute<InetSocketAddress> route) {
    WireSourceRoute temp = (WireSourceRoute)route;
    ArrayList<InetSocketAddress> result = new ArrayList<InetSocketAddress>(temp.getPath());
    
    Collections.reverse(result);
    
    return new WireSourceRoute(result);
  }

  public SourceRoute<InetSocketAddress> getSourceRoute(
      InetSocketAddress local, InetSocketAddress dest) {
    return new WireSourceRoute(local, dest);
  }

  public SourceRoute<InetSocketAddress> getSourceRoute(InetSocketAddress local) {
    return new WireSourceRoute(local);
  }
}
