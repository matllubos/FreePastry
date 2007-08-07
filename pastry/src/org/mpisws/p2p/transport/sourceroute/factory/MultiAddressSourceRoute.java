package org.mpisws.p2p.transport.sourceroute.factory;

import java.io.IOException;
import java.util.List;

import org.mpisws.p2p.transport.multiaddress.MultiInetSocketAddress;
import org.mpisws.p2p.transport.sourceroute.SourceRoute;

import rice.p2p.commonapi.rawserialization.OutputBuffer;

public class MultiAddressSourceRoute extends SourceRoute<MultiInetSocketAddress> {
  
  MultiAddressSourceRoute(MultiInetSocketAddress local, MultiInetSocketAddress remote) {
    super(local, remote);
  }

  MultiAddressSourceRoute(List<MultiInetSocketAddress> path) {
    super(path);
  }

  MultiAddressSourceRoute(MultiInetSocketAddress address) {
    super(address);
  }

  public void serialize(OutputBuffer buf) throws IOException {
    buf.writeByte((byte) path.size());
    for (MultiInetSocketAddress i : path) {
      i.serialize(buf);
    }
  }

  public int getSerializedLength() {
    int ret = 5; // version+numhops

    // the size of all the EISAs
    for (MultiInetSocketAddress i : path) {
      ret += i.getSerializedLength();
    }
    return ret;
  }
  
  List<MultiInetSocketAddress> getPath() {
    return path;    
  }
}
