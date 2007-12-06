package rice.pastry.socket.nat.rendezvous;

import org.mpisws.p2p.transport.rendezvous.PilotManager;
import org.mpisws.p2p.transport.rendezvous.RendezvousContact;

import rice.pastry.NodeHandle;
import rice.pastry.NodeSetEventSource;
import rice.pastry.NodeSetListener;
import rice.pastry.leafset.LeafSet;

/**
 * Notifies the pilot strategy of leafset changes involving non-natted nodes.
 * 
 * Only instantiate this on NATted nodes.
 * 
 * @author Jeff Hoye
 *
 */
public class LeafSetPilotStrategy<Identifier extends RendezvousContact> implements NodeSetListener {
  LeafSet leafSet;
  PilotManager<Identifier> manager;
  
  public LeafSetPilotStrategy(LeafSet leafSet, PilotManager<Identifier> manager) {
    this.leafSet = leafSet;
    this.manager = manager;
    
    leafSet.addNodeSetListener(this);
  }

  public void nodeSetUpdate(NodeSetEventSource nodeSetEventSource, NodeHandle handle, boolean added) {
    Identifier nh = (Identifier)handle;
    if (nh.canContactDirect()) {
      if (added) {
        manager.openPilot(nh, null);
      } else {
        manager.closePilot(nh);        
      }
    }
  }
  
}
