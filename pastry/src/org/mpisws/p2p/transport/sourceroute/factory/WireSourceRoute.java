package org.mpisws.p2p.transport.sourceroute.factory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;

import org.mpisws.p2p.transport.sourceroute.SourceRoute;

import rice.p2p.commonapi.rawserialization.OutputBuffer;

public class WireSourceRoute extends SourceRoute<InetSocketAddress> {
  
  WireSourceRoute(InetSocketAddress local, InetSocketAddress remote) {
    super(local, remote);
  }

  WireSourceRoute(List<InetSocketAddress> path) {
    super(path);
  }

  WireSourceRoute(InetSocketAddress address) {
    super(address);
  }

  public void serialize(OutputBuffer buf) throws IOException {
    buf.writeByte((byte) path.size());
    for (InetSocketAddress i : path) {
      buf.write(i.getAddress().getAddress(),0,4);
      buf.writeShort((short)i.getPort());
    }
  }

  public int getSerializedLength() {
    int ret = 5; // version+numhops

    // IPV4+port
    ret += path.size() * 6;
    
    return ret;
  }
  
  List<InetSocketAddress> getPath() {
    return path;    
  }
}
