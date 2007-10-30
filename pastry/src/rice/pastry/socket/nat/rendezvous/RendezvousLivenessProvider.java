package rice.pastry.socket.nat.rendezvous;

import java.util.Map;

import org.mpisws.p2p.transport.liveness.LivenessListener;
import org.mpisws.p2p.transport.liveness.LivenessProvider;

/**
 * TODO: Remove Abstract
 *
 * This class forces a RendezvousApp.updateRendezvousInfo() when the 
 * NodeHandle is found faulty.
 * 
 * @author Jeff Hoye
 */
public abstract class RendezvousLivenessProvider implements LivenessProvider {

}
