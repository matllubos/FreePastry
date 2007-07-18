package rice.pastry.transport;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.mpisws.p2p.transport.multiaddress.MultiInetSocketAddress;
import org.mpisws.p2p.transport.sourceroute.manager.simple.NextHopStrategy;

import rice.pastry.NodeHandle;
import rice.pastry.leafset.LeafSet;
import rice.pastry.socket.SocketNodeHandle;

public class LeafSetNHStrategy implements NextHopStrategy<MultiInetSocketAddress>{
  LeafSet ls;
  
  public void setLeafSet(LeafSet ls) {
    this.ls = ls;
  }
  
  public Collection<MultiInetSocketAddress> getNextHops(MultiInetSocketAddress destination) {
    if (ls == null) return null;
    
    Set<MultiInetSocketAddress> ret = new HashSet<MultiInetSocketAddress>();
    
    List<NodeHandle> handles = ls.asList();
    for (NodeHandle handle : handles) {
      ret.add(((SocketNodeHandle)handle).eaddress); 
    }

    // don't include the direct route
    ret.remove(destination);
    return ret;
  }

}
