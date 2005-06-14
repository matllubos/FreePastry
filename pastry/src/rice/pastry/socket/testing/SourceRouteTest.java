package rice.pastry.socket.testing;

import java.net.*;
import java.util.*;

import rice.environment.Environment;
import rice.pastry.messaging.*;
import rice.pastry.socket.*;
import rice.pastry.standard.*;
import rice.pastry.testing.*;

public class SourceRouteTest {
  
  public static class TestMessage extends Message {
    public TestMessage() {
      super(new StandardAddress(TestMessage.class, "monkey"));
    }
  }
 
  public static void main2(String[] args) throws Exception {
    Environment env = new Environment();
    SocketPastryNodeFactory factory = new SocketPastryNodeFactory(new RandomNodeIdFactory(env), 20001, env);
    /*
    InetSocketAddress bind = new InetSocketAddress(InetAddress.getLocalHost(), 10001);
    InetSocketAddress local = new InetSocketAddress(InetAddress.getLocalHost(), 10001);
    InetSocketAddress remote = new InetSocketAddress("planetlab10.millennium.berkeley.edu", 15001);

    factory.verifyConnection(bind, local, remote);

    Thread.sleep(1000);
    
    factory.verifyConnection(bind, local, remote);
    */
    
    System.out.println("DONE");
  }
  
  static SocketPastryNode node1,node2,node3,node4,node5,node6,node7,node8,node9,node10;
  
  public static class TestMessage2 extends Message {
    
    byte[] payload;
    
    public TestMessage2() {
      super(new StandardAddress(27));
      payload = new byte[10000];
    }
  }
  
  public static void main(String[] args) throws Exception {
    Environment env = new Environment();
    SocketPastryNodeFactory factory = new SocketPastryNodeFactory(new RandomNodeIdFactory(env), 20001, env);
    
    new Thread() {
      int k=0;
      
      public void printSockets(SocketPastryNode node) {
        if (node == null)
          return;
        
        Hashtable sockets = node.getSocketSourceRouteManager().getManager().sockets;
        Object[] o = sockets.keySet().toArray();
        
        for(int i=0; i<o.length; i++)
          System.out.println("SOCKET::\t" + k + "\t" + ((SocketNodeHandle) node.getLocalNodeHandle()).getEpochAddress() + "\t" + o[i]);
      }
      
      public void run() {
        while (true) {
          try {
            Thread.sleep(5000);
            k++;
            
            printSockets(node1);
            printSockets(node2);
            printSockets(node3);
            printSockets(node4);
            printSockets(node5);
            printSockets(node6);
            printSockets(node7);
            printSockets(node8);
            printSockets(node9);
            printSockets(node10);
          } catch (Exception e) {
            System.out.println("ERROR: CNAOCAN " + e);
          }
        }
      }
    }.start();
    
    final InetSocketAddress address1 = new InetSocketAddress(InetAddress.getLocalHost(), 20001);
    InetSocketAddress address2 = new InetSocketAddress(InetAddress.getLocalHost(), 20002);
    InetSocketAddress address3 = new InetSocketAddress(InetAddress.getLocalHost(), 20003);
    InetSocketAddress address4 = new InetSocketAddress(InetAddress.getLocalHost(), 20004);
    InetSocketAddress address5 = new InetSocketAddress(InetAddress.getLocalHost(), 20005);
    InetSocketAddress address6 = new InetSocketAddress(InetAddress.getLocalHost(), 20006);
    InetSocketAddress address7 = new InetSocketAddress(InetAddress.getLocalHost(), 20007);
    InetSocketAddress address8 = new InetSocketAddress(InetAddress.getLocalHost(), 20008);
    InetSocketAddress address9 = new InetSocketAddress(InetAddress.getLocalHost(), 20009);
    InetSocketAddress address10 = new InetSocketAddress(InetAddress.getLocalHost(), 20010);
    
    System.out.println("Starting 1...");
    node1 = (SocketPastryNode) factory.newNode(null);
    Thread.sleep(1000);
    System.out.println("Starting 2...");
    node2 = (SocketPastryNode) factory.newNode(factory.getNodeHandle(address1));
    Thread.sleep(4000);
    System.out.println("Starting 3...");
    node3 = (SocketPastryNode) factory.newNode(factory.getNodeHandle(address2));
    Thread.sleep(4000);
    
  /*  new Thread() {
      public void run() {
        try {
          while (true) {
            Thread.sleep(1000);
            node2.getSocketSourceRouteManager().send(((SocketNodeHandle) node1.getLocalNodeHandle()).getEpochAddress(), 
                                                     new TestMessage2());
          }
        } catch (Exception e) {
          System.out.println("NBAHL" + e);
        }
      }
    }.start();
    Thread.sleep(4000);

    System.out.println("STALLING..."); */
    
 //   node1.getSocketSourceRouteManager().getManager().stall();
    
    System.out.println("Starting 4...");
    node4 = (SocketPastryNode) factory.newNode(factory.getNodeHandle(address3));
    Thread.sleep(10000);
    System.out.println("Starting 5...");
    node5 = (SocketPastryNode) factory.newNode(factory.getNodeHandle(address4));
    Thread.sleep(13000); 
    System.out.println("Starting 6...");
    node6 = (SocketPastryNode) factory.newNode(factory.getNodeHandle(address5));
    Thread.sleep(16000);
    System.out.println("Starting 7...");
    node7 = (SocketPastryNode) factory.newNode(factory.getNodeHandle(address6));
    Thread.sleep(18000);
    System.out.println("Starting 8...");
    node8 = (SocketPastryNode) factory.newNode(factory.getNodeHandle(address7));
    Thread.sleep(20000);
    System.out.println("Starting 9...");
    node9 = (SocketPastryNode) factory.newNode(factory.getNodeHandle(address8));
    Thread.sleep(20000);
    System.out.println("Starting 10...");
    node10 = (SocketPastryNode) factory.newNode(factory.getNodeHandle(address9)); 
    Thread.sleep(40000);
    
    
    System.out.println("Source Routes...");
    System.out.println("Node 1 (20001) -> ");
    printRoutes(node1.getSocketSourceRouteManager());
    System.out.println("Node 2 (20002) -> ");
    printRoutes(node2.getSocketSourceRouteManager());
    System.out.println("Node 3 (20003) -> ");
    printRoutes(node3.getSocketSourceRouteManager());
    System.out.println("Node 4 (20004) -> ");
    printRoutes(node4.getSocketSourceRouteManager());
    System.out.println("Node 5 (20005) -> ");
    printRoutes(node5.getSocketSourceRouteManager());
    System.out.println("Node 6 (20006) -> ");
    printRoutes(node6.getSocketSourceRouteManager());
    System.out.println("Node 7 (20007) -> ");
    printRoutes(node7.getSocketSourceRouteManager());
    System.out.println("Node 8 (20008) -> ");
    printRoutes(node8.getSocketSourceRouteManager());
    System.out.println("Node 9 (20009) -> ");
    printRoutes(node9.getSocketSourceRouteManager());
    System.out.println("Node 10 (20010) -> ");
    printRoutes(node10.getSocketSourceRouteManager()); 
    Thread.sleep(10000);

    System.out.println("Starting Pastry test...");
    
    PastryNetworkTest test = new PastryNetworkTest(env, factory, new InetSocketAddress(InetAddress.getLocalHost(), 20002));
    test.start();

   
//    PingManager.FLAG = false;
//    Thread.sleep(120000);
    
    node2.resign();
    
    Thread.sleep(40000);
    
    System.out.println("Later Source Routes...");
    System.out.println("Node 1 (20001) -> ");
    printRoutes(node1.getSocketSourceRouteManager());
    System.out.println("Node 3 (20003) -> ");
    printRoutes(node3.getSocketSourceRouteManager());
    System.out.println("Node 4 (20004) -> ");
    printRoutes(node4.getSocketSourceRouteManager());
    System.out.println("Node 5 (20005) -> ");
    printRoutes(node5.getSocketSourceRouteManager());
    System.out.println("Node 6 (20006) -> ");
    printRoutes(node6.getSocketSourceRouteManager());
    System.out.println("Node 8 (20008) -> ");
    printRoutes(node8.getSocketSourceRouteManager());
    System.out.println("Node 9 (20009) -> ");
    printRoutes(node9.getSocketSourceRouteManager());
    System.out.println("Node 10 (20010) -> ");
    printRoutes(node10.getSocketSourceRouteManager()); 
    Thread.sleep(10000);    
    
    /*
    Thread.sleep(10000);
    
    System.out.println("Starting source route creation...");
    
    node1.getSocketSourceRouteManager().getManager().send(new SourceRoute(new InetSocketAddress[] {address2, address3, address5, address4}), new TestMessage());

    Thread.sleep(10000);
    
    System.out.println("Starting source route ping...");
    
    node1.getSocketSourceRouteManager().getManager().ping(new SourceRoute(new InetSocketAddress[] {address2, address3, address5, address4}), new PingResponseListener() {
      public void pingResponse(SourceRoute path, long RTT, long timeHeardFrom) {}
    }); */
  }
  
  public static void printRoutes(SocketSourceRouteManager manager) {
    HashMap best = manager.getBest();
    
    Iterator keys = best.keySet().iterator();
    
    while (keys.hasNext()) {
      SourceRoute next = (SourceRoute) best.get(keys.next());
      
      System.out.println("\t" + next);
    }
    
    System.out.println();
  }
  
}
