package rice.pastry.socket.nat.rendezvous;

import java.io.IOException;
import java.net.InetAddress;

import rice.environment.Environment;
import rice.pastry.NodeIdFactory;
import rice.pastry.socket.SocketPastryNodeFactory;
import rice.pastry.socket.nat.NATHandler;

public class RendezvousSocketPastryNodeFactory extends SocketPastryNodeFactory {

  public RendezvousSocketPastryNodeFactory(NodeIdFactory nf, InetAddress bindAddress, int startPort, Environment env, NATHandler handler) throws IOException {
    super(nf, bindAddress, startPort, env, handler);
    // TODO Auto-generated constructor stub
  }

  public RendezvousSocketPastryNodeFactory(NodeIdFactory nf, int startPort, Environment env) throws IOException {
    super(nf, startPort, env);
    // TODO Auto-generated constructor stub
  }
  
}
