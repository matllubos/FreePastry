package rice.pastry.boot;

import org.mpisws.p2p.transport.liveness.LivenessProvider;

import rice.p2p.commonapi.NodeHandle;
import rice.p2p.commonapi.rawserialization.RawMessage;
import rice.pastry.PastryNode;

/**
 * This does PNS before booting.
 * 
 * @author Jeff Hoye
 *
 */
public class PNSBootstrapApp extends BootstrapApp {

  public PNSBootstrapApp(PastryNode pn, LivenessProvider<NodeHandle> livenessTL) {
    super(pn, livenessTL);
  }


}
