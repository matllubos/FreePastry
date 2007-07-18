package rice.pastry.transport;

import java.io.IOException;

import org.mpisws.p2p.transport.P2PSocket;
import org.mpisws.p2p.transport.P2PSocketReceiver;
import org.mpisws.p2p.transport.commonapi.TransportLayerNodeHandle;
import org.mpisws.p2p.transport.multiaddress.MultiInetSocketAddress;

import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.p2p.commonapi.appsocket.AppSocketReceiver;
import rice.pastry.NodeHandle;

public class AppSocketReceiverWrapper implements
    P2PSocketReceiver<NodeHandle> {

  private AppSocketReceiver receiver;
  private SocketAdapter socket;
  private Logger logger;

  public AppSocketReceiverWrapper(AppSocketReceiver receiver, SocketAdapter socket, Environment env) {
    this.receiver = receiver;
    this.socket = socket;
    this.logger = env.getLogManager().getLogger(AppSocketReceiverWrapper.class, null);    
  }

  public void receiveException(P2PSocket<NodeHandle> s, IOException ioe) {
    receiver.receiveException(socket, ioe);
  }

  public void receiveSelectResult(P2PSocket<NodeHandle> s,
      boolean canRead, boolean canWrite) throws IOException {
//    logger.log("rSR("+canRead+","+canWrite+")");
    receiver.receiveSelectResult(socket, canRead, canWrite);
  }

}
