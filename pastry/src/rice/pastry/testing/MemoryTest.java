/*
 * Created on Aug 8, 2005
 */
package rice.pastry.testing;

import java.io.*;
import java.io.PrintStream;
import java.net.*;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.ArrayList;

import rice.environment.Environment;
import rice.pastry.*;
import rice.pastry.socket.*;
import rice.pastry.socket.SocketPastryNodeFactory;
import rice.pastry.standard.RandomNodeIdFactory;

/**
 * The purpose of this test is to verify that FreePastry is properly shutting down, without having 
 * to destroy the JVM.
 * 
 * @author Jeff Hoye
 */
public class MemoryTest {

  /**
   * The idea is to keep a ring of about 10 nodes alive, but one by one recycle the nodes out.  In the end, 
   * creating a whole lot of nodes, to see if they are being properly cleaned up.
   *
   * TODO: make this return a boolean:
   * 
   * Record the memory at start (M1), then after creating the ringSize*2 nodes (M2)
   * (M2-M1/ringSize) is the approx memory for 1 node.
   * 
   * M3 = (M2-M1) which is basically the amount of memory that our ring of 10 should take up.
   * 
   * at the end of the test, record M4.  
   * 
   * if M4 > (M1+2*M3) then we probably have a leak, fail.
   * 
   * TODO: test this with other environment settings
   */
  public static void testOneEnvironment() throws Exception {
    //System.setOut(new PrintStream(new FileOutputStream("memtest.txt")));
    
    // setup
    int startPort = 5438;
    int ringSize = 10;
    int numNodes = 100;
    
    LinkedList nodes = new LinkedList();
    Runtime run = Runtime.getRuntime();
    long memUsed = run.totalMemory()-run.freeMemory();
    System.out.println("Memory:"+memUsed);
    Environment env = new Environment();    
    env.getParameters().setBoolean("pastry_factory_selectorPerNode", false);
    env.getParameters().setBoolean("pastry_factory_processorPerNode", false);
    env.getParameters().setInt("pastry_socket_scm_ping_delay", 1000);
    env.getParameters().setInt("pastry_socket_scm_ping_jitter", 500);
    env.getParameters().setInt("pastry_socket_scm_num_ping_tries", 2);
    
    NodeIdFactory nidFactory = new RandomNodeIdFactory(env);
    
    //InetAddress localAddress = InetAddress.getByName("139.19.64.79");
    InetAddress localAddress = InetAddress.getLocalHost();
    
    PastryNodeFactory factory = new SocketPastryNodeFactory(nidFactory, localAddress, startPort, env);

    InetSocketAddress bootaddress = new InetSocketAddress(localAddress, startPort);
    
    int curNode = 0; 
    // make initial ring of 10 nodes
    for (;curNode < numNodes; curNode++) {
      NodeHandle bootHandle = ((SocketPastryNodeFactory) factory).getNodeHandle(bootaddress);
      PastryNode node = factory.newNode((rice.pastry.NodeHandle) bootHandle);
      
      long waitTime = env.getTimeSource().currentTimeMillis();
      
      while (!node.isReady()) {
        Thread.sleep(1000);
        long waitedFor = env.getTimeSource().currentTimeMillis() - waitTime;
        //System.out.println("Waited for "+waitedFor+" millis.");
      }
      
      // print the current status
      long waitedFor = env.getTimeSource().currentTimeMillis() - waitTime;
      memUsed = run.totalMemory()-run.freeMemory();
      System.out.println(curNode+"/"+numNodes+" Memory:"+memUsed+" leafset size:"+node.getLeafSet().size()+" "+node+" after "+waitedFor);      

      // always boot off of the previous node
      bootaddress = ((SocketNodeHandle)node.getLocalHandle()).getAddress();
      
      // store the node
      nodes.addLast(node);
      
      // kill a node
      if (curNode > ringSize) {
        PastryNode pn = (PastryNode)nodes.removeFirst(); 
        System.out.println("Destroying pastry node "+pn);
        pn.destroy();
        //System.out.println("Done destroying.");
      }
    }    
    env.destroy();
  }
  
  /**
   * Same test as testOneEnvironment, but also creates/destroys the environment for each node.
   *
   */
  public static void testMultiEnvironment() {
    
  }

  /**
   * Same thing, but with direct
   *
   */
  public static void testDirect() {
     
  }
  
  public static void main(String[] args) throws Exception {
    testOneEnvironment();
    testMultiEnvironment();
  }
}
