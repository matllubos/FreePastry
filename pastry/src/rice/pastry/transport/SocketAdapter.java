package rice.pastry.transport;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.mpisws.p2p.transport.P2PSocket;
import org.mpisws.p2p.transport.P2PSocketReceiver;
import org.mpisws.p2p.transport.commonapi.TransportLayerNodeHandle;
import org.mpisws.p2p.transport.multiaddress.MultiInetSocketAddress;

import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.p2p.commonapi.appsocket.AppSocket;
import rice.p2p.commonapi.appsocket.AppSocketReceiver;
import rice.pastry.NodeHandle;

public class SocketAdapter implements AppSocket {
  P2PSocket<NodeHandle> internal;
  Logger logger;
  Environment environment;
  
  public SocketAdapter(P2PSocket<NodeHandle> socket, Environment env) {
    this.internal = socket;
    this.logger = env.getLogManager().getLogger(SocketAdapter.class, null);
    this.environment = env;
  }

  public void close() {
    internal.close();
  }

  public long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
    return internal.read(dsts, offset, length);
  }

  public void register(boolean wantToRead, boolean wantToWrite, int timeout, AppSocketReceiver receiver) {
//    logger.log("register("+wantToRead+","+wantToWrite+","+receiver+")");
    internal.register(wantToRead, wantToWrite, new AppSocketReceiverWrapper(receiver, this, environment));
  }

  public void shutdownOutput() {
    internal.shutdownOutput();    
  }

  public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
    return internal.write(srcs, offset, length);
  }
}
