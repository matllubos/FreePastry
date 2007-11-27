package rice.pastry.socket.nat.rendezvous;

import java.net.InetSocketAddress;
import java.util.ArrayList;

import org.mpisws.p2p.transport.rendezvous.ChannelOpener;
import org.mpisws.p2p.transport.rendezvous.RendezvousContact;
import org.mpisws.p2p.transport.rendezvous.RendezvousStrategy;

import rice.Continuation;
import rice.p2p.commonapi.Cancellable;
import rice.p2p.commonapi.NodeHandle;
import rice.p2p.util.AttachableCancellable;
import rice.pastry.PastryNode;
import rice.pastry.client.PastryAppl;
import rice.pastry.leafset.LeafSet;
import rice.pastry.messaging.Message;
import rice.pastry.socket.SocketNodeHandle;
import rice.selector.SelectorManager;

/**
 * TODO: make not abstract
 * 
 * @author Jeff Hoye
 *
 */
public class RendezvousApp extends PastryAppl implements RendezvousStrategy<RendezvousSocketNodeHandle> {
  LeafSet leafSet;
  SelectorManager selectorManager;
  
  public RendezvousApp(PastryNode pn) {
    super(pn);
    leafSet = pn.getLeafSet();
    selectorManager = pn.getEnvironment().getSelectorManager();
  }
  
  /**
   * Can be called before you boot, will tell you if you are Firewalled.
   * Should send a message to the bootstrap, who forwards it to another node who sends you the request back.  Should
   * try UDP/TCP.
   * 
   * Returns your external address.
   * 
   * @param bootstrap
   * @param receiveResult
   */
  public void isNatted(NodeHandle bootstrap, Continuation<InetSocketAddress, Exception> receiveResult) {
    
  }

  @Override
  public void messageForAppl(Message msg) {
    // TODO Auto-generated method stub
    
  }

  public Cancellable openChannel(final RendezvousSocketNodeHandle target, 
      final RendezvousSocketNodeHandle rendezvous, 
      final byte[] credentials, 
      final Continuation<Integer, Exception> deliverResultToMe) {
    
    // we don't want state changing, so this can only be called on the selector
    if (!selectorManager.isSelectorThread()) {
      final AttachableCancellable ret = new AttachableCancellable();
      selectorManager.invoke(new Runnable() {
        public void run() {
          ret.attach(openChannel(target, rendezvous, credentials, deliverResultToMe));
        }
      });
      return ret;
    }

    if (target.isConnected()) {
      // TODO: route directly there 
      return null;
    }
    
    // What if he is my nearest neighbor?  This fails an invariant.  Better at least track this...
    // also, there can be dead nodes in the leafset, due to the lease...  Better find that there is a live node between us and the leafset.
    if (leafSet.contains(target)) {
      // it's in the leafset, make sure there is a guy between us
      // find the nearest alive guy to target that is between us to send-direct the request to
      
      // this is the index of target
      int targetIndex = leafSet.getIndex(target);      
      
      // this is the nearest neighbor on the side of target
      int nearestNeighborIndex = 1;      
      if (targetIndex < 0) nearestNeighborIndex = -1;
      
      // this is the direction we count from target to nearestNeighbor
      int direction = -nearestNeighborIndex;

      for (int i = targetIndex+direction; i != nearestNeighborIndex; i++) {
        RendezvousSocketNodeHandle nh = (RendezvousSocketNodeHandle)leafSet.get(i);
        
        // we can send a message to nh
        if (nh.isConnected()) {
          // TODO: send the message to this node, if it fails, call this.openChannel() again...
        }
      }
    }
    
    
    // TODO Auto-generated method stub
    return null;
  }


  
  
  
}
