package rice.pastry.socket.testing;

import java.net.*;
import java.util.*;

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
    SocketPastryNodeFactory factory = new SocketPastryNodeFactory(new RandomNodeIdFactory(), 20001);
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
  
  public static void main(String[] args) throws Exception {
    SocketPastryNodeFactory factory = new SocketPastryNodeFactory(new RandomNodeIdFactory(), 20001);
    
    InetSocketAddress address1 = new InetSocketAddress(InetAddress.getLocalHost(), 20001);
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
    SocketPastryNode node1 = (SocketPastryNode) factory.newNode(null);
    Thread.sleep(1000);
    System.out.println("Starting 2...");
    SocketPastryNode node2 = (SocketPastryNode) factory.newNode(factory.getNodeHandle(address1));
    Thread.sleep(4000);
    System.out.println("Starting 3...");
    SocketPastryNode node3 = (SocketPastryNode) factory.newNode(factory.getNodeHandle(address2));
    Thread.sleep(20000);
    System.out.println("Starting 4...");
    SocketPastryNode node4 = (SocketPastryNode) factory.newNode(factory.getNodeHandle(address3));
    Thread.sleep(20000);
    System.out.println("Starting 5...");
    SocketPastryNode node5 = (SocketPastryNode) factory.newNode(factory.getNodeHandle(address4));
    Thread.sleep(20000); 
    System.out.println("Starting 6...");
    SocketPastryNode node6 = (SocketPastryNode) factory.newNode(factory.getNodeHandle(address5));
    Thread.sleep(20000);
    System.out.println("Starting 7...");
    SocketPastryNode node7 = (SocketPastryNode) factory.newNode(factory.getNodeHandle(address6));
    Thread.sleep(20000);
    System.out.println("Starting 8...");
    SocketPastryNode node8 = (SocketPastryNode) factory.newNode(factory.getNodeHandle(address7));
    Thread.sleep(20000);
    System.out.println("Starting 9...");
    SocketPastryNode node9 = (SocketPastryNode) factory.newNode(factory.getNodeHandle(address8));
    Thread.sleep(20000);
    System.out.println("Starting 10...");
    SocketPastryNode node10 = (SocketPastryNode) factory.newNode(factory.getNodeHandle(address9)); 
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
    
    PastryNetworkTest test = new PastryNetworkTest(factory, new InetSocketAddress(InetAddress.getLocalHost(), 20002));
    test.start();

   
//    PingManager.FLAG = false;
//    Thread.sleep(120000);
    
    node2.resign();
    
    Thread.sleep(20000);
    
    System.out.println("Later Source Routes...");
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