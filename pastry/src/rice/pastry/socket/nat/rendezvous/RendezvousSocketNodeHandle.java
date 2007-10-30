package rice.pastry.socket.nat.rendezvous;

import java.io.IOException;

import org.mpisws.p2p.transport.multiaddress.MultiInetSocketAddress;

import rice.p2p.commonapi.rawserialization.OutputBuffer;
import rice.pastry.Id;
import rice.pastry.socket.SocketNodeHandle;
import rice.pastry.transport.TLPastryNode;

/**
 * Maintains RendezvousInfo with the NodeHandle
 * 
 * @author Jeff Hoye
 */
public class RendezvousSocketNodeHandle extends SocketNodeHandle {

  RendezvousSocketNodeHandle(MultiInetSocketAddress eisa, long epoch, Id id, TLPastryNode node) {
    super(eisa, epoch, id, node);
    // TODO Auto-generated constructor stub
  }

  @Override
  public void serialize(OutputBuffer buf) throws IOException {
    // TODO suffix w/ rendezvous stuff
    super.serialize(buf);
  }

  
}
