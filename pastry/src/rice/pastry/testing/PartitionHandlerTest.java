package rice.pastry.testing;

import java.io.IOException;
import java.net.InetSocketAddress;

import rice.environment.Environment;
import rice.pastry.NodeHandle;
import rice.pastry.PastryNode;
import rice.pastry.socket.SocketPastryNodeFactory;
import rice.pastry.standard.PartitionHandler;
import rice.pastry.standard.RandomNodeIdFactory;

public class PartitionHandlerTest {

  private static final int PORT_A = 2323;
  private static final int PORT_B = 4646;

  /**
   * @param args
   * @throws IOException 
   * @throws InterruptedException 
   */
  public static void main(String[] args) throws IOException, InterruptedException {
    Environment env;
    env = new Environment();
    env.getParameters().setString("loglevel", "ALL");
    SocketPastryNodeFactory factoryA = new SocketPastryNodeFactory(new RandomNodeIdFactory(env), PORT_A, env);
    SocketPastryNodeFactory factoryB = new SocketPastryNodeFactory(new RandomNodeIdFactory(env), PORT_B, env);
    
    NodeHandle bootstrapA = factoryA.getNodeHandle(new InetSocketAddress("localhost", PORT_A));
    PastryNode a = factoryA.newNode(bootstrapA);
    NodeHandle bootstrapB = factoryB.getNodeHandle(new InetSocketAddress("localhost", PORT_B));
    PastryNode b = factoryB.newNode(bootstrapB);
    
    PartitionHandler handlerA = new PartitionHandler(a, factoryA, null);
    PartitionHandler handlerB = new PartitionHandler(b, factoryB, null);
    
    // wait for a to boot
    // wait for b to boot
    
    synchronized (a) {
      a.wait(30000);
    }
    
    handlerA.rejoin(factoryA.getNodeHandle(new InetSocketAddress("localhost", PORT_B)));
    
  }

}
