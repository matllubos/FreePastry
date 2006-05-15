package rice.tutorial.appsocket;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Iterator;
import java.util.Vector;

import rice.environment.Environment;
import rice.environment.params.simple.SimpleParameters;
import rice.p2p.commonapi.*;
import rice.pastry.NodeHandle;
import rice.pastry.NodeIdFactory;
import rice.pastry.PastryNode;
import rice.pastry.PastryNodeFactory;
import rice.pastry.commonapi.PastryIdFactory;
import rice.pastry.direct.*;
import rice.pastry.leafset.LeafSet;
import rice.pastry.socket.SocketPastryNodeFactory;
import rice.pastry.standard.RandomNodeIdFactory;

/**
 * This tutorial shows how to setup a FreePastry node using the Socket Protocol.
 * 
 * @author Jeff Hoye
 */
public class DistTutorial {

  public static final boolean USE_DIRECT = false;

  
  // this will keep track of our applications
  Vector apps = new Vector();
  
  /**
   * This constructor launches numNodes PastryNodes.  They will bootstrap 
   * to an existing ring if one exists at the specified location, otherwise
   * it will start a new ring.
   * 
   * @param bindport the local port to bind to 
   * @param bootaddress the IP:port of the node to boot from
   * @param numNodes the number of nodes to create in this JVM
   * @param env the environment for these nodes
   */
  public DistTutorial(int bindport, InetSocketAddress bootaddress, int numNodes, Environment env, boolean useDirect) throws Exception {
    
    // Generate the NodeIds Randomly
    NodeIdFactory nidFactory = new RandomNodeIdFactory(env);
    

    // construct the PastryNodeFactory
    PastryNodeFactory factory;
    if (useDirect) {
      NetworkSimulator sim = new EuclideanNetwork(env);
      factory = new DirectPastryNodeFactory(nidFactory, sim, env);
    } else {
      factory = new SocketPastryNodeFactory(nidFactory, bindport, env);
    }
    
    IdFactory idFactory = new PastryIdFactory(env);
    
    NodeHandle bootHandle = null; //((SocketPastryNodeFactory)factory).getNodeHandle(bootaddress);
    
    // loop to construct the nodes/apps
    for (int curNode = 0; curNode < numNodes; curNode++) {
      // This will return null if we there is no node at that location
  
      // construct a node, passing the null boothandle on the first loop will cause the node to start its own ring
      PastryNode node = factory.newNode(bootHandle);
      if (bootHandle == null) {
        if (useDirect) {
          bootHandle = node.getLocalHandle();
        } else {
          // This will return null if we there is no node at that location
          bootHandle = ((SocketPastryNodeFactory)factory).getNodeHandle(bootaddress);
        }
      }
      
      // the node may require sending several messages to fully boot into the ring
      while(!node.isReady()) {        
        // delay so we don't busy-wait
        Thread.sleep(100);
      }
      
      System.out.println("Finished creating new node "+node);
      
      // construct a new MyApp
      MyApp app = new MyApp(node, idFactory);
      
      apps.add(app);
    }
      
    // wait 10 seconds
//    Thread.sleep(10000);
    Iterator appIterator = apps.iterator();
    while(appIterator.hasNext()) {
      MyApp app = (MyApp)appIterator.next();
      PastryNode node = (PastryNode)app.getNode();
      
      // send directly to my leafset
      LeafSet leafSet = node.getLeafSet();
      System.out.println(leafSet);
    }    
    
      
    // for each app
    appIterator = apps.iterator();
    while(appIterator.hasNext()) {
      MyApp app = (MyApp)appIterator.next();
      PastryNode node = (PastryNode)app.getNode();
      
      // send directly to my leafset
      LeafSet leafSet = node.getLeafSet();
      
      // this is a typical loop to cover your leafset.  Note that if the leafset
      // overlaps, then duplicate nodes will be sent to twice
      for (int i=-leafSet.ccwSize(); i<=leafSet.cwSize(); i++) {
        if (i != 0) { // don't send to self
          // select the item
          NodeHandle nh = leafSet.get(i);
          
          // send the message directly to the node
          app.sendMyMsgDirect(nh);   
          
          // wait a bit
          Thread.sleep(100);
        }
      }
    }
    
    appIterator = apps.iterator();
    while(appIterator.hasNext()) {
      MyApp app = (MyApp)appIterator.next();
      PastryNode node = (PastryNode)app.getNode();
      app.sendMyMsgDirect(node.getLocalNodeHandle());   
    }
  }

  /**
   * Usage: 
   * java [-cp FreePastry-<version>.jar] rice.tutorial.lesson4.DistTutorial localbindport bootIP bootPort numNodes
   * example java rice.tutorial.DistTutorial 9001 pokey.cs.almamater.edu 9001 10
   */
  public static void main(String[] args) throws Exception {
    boolean useDirect = USE_DIRECT;
    if (args.length == 5) {
      if (args[4].equalsIgnoreCase("-direct")) {
        useDirect = true;
      } else {
        useDirect = false; 
      }
    }
      
    
    // Loads pastry settings
    Environment env;
    if (useDirect) {
      env = Environment.directEnvironment();
    } else {
      env = new Environment(); 
    }
    
    try {
      // the port to use locally
      int bindport = Integer.parseInt(args[0]);
      
      // build the bootaddress from the command line args
      InetAddress bootaddr = InetAddress.getByName(args[1]);
      int bootport = Integer.parseInt(args[2]);
      InetSocketAddress bootaddress = new InetSocketAddress(bootaddr,bootport);
  
      // the number of nodes to use
      int numNodes = Integer.parseInt(args[3]);    
      
      // launch our node!
      DistTutorial dt = new DistTutorial(bindport, bootaddress, numNodes, env, useDirect);
    } catch (Exception e) {
      // remind user how to use
      System.out.println("Usage:"); 
      System.out.println("java [-cp FreePastry-<version>.jar] rice.tutorial.lesson4.DistTutorial localbindport bootIP bootPort numNodes");
      System.out.println("example java rice.tutorial.DistTutorial 9001 pokey.cs.almamater.edu 9001 10");
      throw e; 
    }
  }
}
