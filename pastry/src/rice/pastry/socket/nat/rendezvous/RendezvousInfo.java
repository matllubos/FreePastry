package rice.pastry.socket.nat.rendezvous;

import java.util.List;

import rice.p2p.commonapi.NodeHandle;

/**
 * Should eventually be signed.
 * 
 * @author Jeff Hoye
 *
 */
public interface RendezvousInfo {
  public long timeStamp();
  public boolean isNATted();
  public boolean isFaulty();
  public List<NodeHandle> getRendezvousNodes();
}
