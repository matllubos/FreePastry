package rice.pastry.socket.nat.rendezvous;

import java.io.IOException;

import org.mpisws.p2p.transport.multiaddress.MultiInetSocketAddress;

import rice.p2p.commonapi.rawserialization.InputBuffer;
import rice.pastry.Id;
import rice.pastry.NodeHandle;
import rice.pastry.socket.SocketNodeHandle;
import rice.pastry.socket.SocketNodeHandleFactory;
import rice.pastry.transport.TLPastryNode;

public class RendezvousSNHFactory extends SocketNodeHandleFactory {

  public RendezvousSNHFactory(TLPastryNode pn) {
    super(pn);
  }

  @Override
  public SocketNodeHandle getNodeHandle(MultiInetSocketAddress i, long epoch, Id id) {
    return getNodeHandle(i,epoch, id, (byte)0);
  }
  
  public SocketNodeHandle getNodeHandle(MultiInetSocketAddress i, long epoch, Id id, byte contactState) {
    SocketNodeHandle handle = new RendezvousSocketNodeHandle(i, epoch, id, pn, contactState);
    
    return (SocketNodeHandle)coalesce(handle);
  }

  @Override
  public NodeHandle readNodeHandle(InputBuffer buf) throws IOException {
    return coalesce(RendezvousSocketNodeHandle.build(buf, pn));
  }
}
