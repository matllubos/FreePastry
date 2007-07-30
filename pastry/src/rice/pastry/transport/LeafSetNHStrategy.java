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
    
    Collection<MultiInetSocketAddress> ret = walkLeafSet(destination, 8);
    
    // don't include the direct route
//    ret.remove(destination);
    return ret;
  }
  
  private Collection<MultiInetSocketAddress> walkLeafSet(MultiInetSocketAddress destination, int numRequested) {
    Collection<MultiInetSocketAddress> result = new HashSet<MultiInetSocketAddress>();
    LeafSet leafset = ls;
    for (int i = 1; i < leafset.maxSize()/2; i++) {      
      SocketNodeHandle snh = (SocketNodeHandle)leafset.get(-i);
      if (snh != null && !snh.eaddress.equals(destination)) {
        result.add(snh.eaddress);
        if (result.size() >= numRequested) return result;
      }
      snh = (SocketNodeHandle)leafset.get(i);
      if (snh != null && !snh.eaddress.equals(destination)) {
        result.add(snh.eaddress);
        if (result.size() >= numRequested) return result;
      }
    }
    return result;
  }
}
