package rice.pastry.boot;

import java.net.InetSocketAddress;
import java.util.Collection;

import org.mpisws.p2p.transport.TransportLayer;
import org.mpisws.p2p.transport.liveness.LivenessListener;
import org.mpisws.p2p.transport.liveness.LivenessProvider;

import rice.p2p.commonapi.NodeHandle;
import rice.p2p.commonapi.rawserialization.RawMessage;
import rice.pastry.PastryNode;
import rice.pastry.client.PastryAppl;
import rice.pastry.messaging.Message;
import rice.pastry.transport.TLPastryNode;

/**
 * Simple bootstrapper.  Just tries teh bootaddresses in order.
 * 
 * @author Jeff Hoye
 *
 */
public class BootstrapApp extends PastryAppl implements Bootstrapper, LivenessListener<NodeHandle> {
  
  public BootstrapApp(PastryNode pn, LivenessProvider<NodeHandle> live) {
    super(pn);
    
    live.addLivenessListener(this);
  }

  @Override
  public void messageForAppl(Message msg) {
    // TODO Auto-generated method stub

  }

  public void boot(Collection bootaddresses) {
    
  }

  public void livenessChanged(NodeHandle i, int val) {
    // TODO Auto-generated method stub
    
  }
  
//  protected NodeHandle getNodeHandle(InetSocketAddress address) {
//    
//  }

}
