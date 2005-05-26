package rice.tutorial.lesson6;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Random;
import java.util.Vector;

import rice.p2p.commonapi.NodeHandle;
import rice.pastry.NodeIdFactory;
import rice.pastry.PastryNode;
import rice.pastry.PastryNodeFactory;
import rice.pastry.socket.SocketPastryNodeFactory;
import rice.pastry.standard.RandomNodeIdFactory;

/**
 * This tutorial shows how to setup a FreePastry node using the Socket Protocol.
 * 
 * @author Jeff Hoye
 */
public class ScribeTutorial {

  /**
   * this will keep track of our applications
   */ 
  Vector apps = new Vector();
  
  /**
   * Based on the rice.tutorial.lesson4.DistTutorial
   * 
   * This constructor launches numNodes PastryNodes.  They will bootstrap 
   * to an existing ring if one exists at the specified location, otherwise
   * it will start a new ring.
   * 
   * @param bindport the local port to bind to 
   * @param bootaddress the IP:port of the node to boot from
   * @param numNodes the number of nodes to create in this JVM
   */
  public ScribeTutorial(int bindport, InetSocketAddress bootaddress, int numNodes) throws Exception {
    // Generate the NodeIds Randomly
    NodeIdFactory nidFactory = new RandomNodeIdFactory();
    
    // construct the PastryNodeFactory, this is how we use rice.pastry.socket
    PastryNodeFactory factory = new SocketPastryNodeFactory(nidFactory, bindport);

   
    // loop to construct the nodes/apps
    for (int curNode = 0; curNode < numNodes; curNode++) {
      // This will return null if we there is no node at that location
      rice.pastry.NodeHandle bootHandle = ((SocketPastryNodeFactory)factory).getNodeHandle(bootaddress);
  
      // construct a node, passing the null boothandle on the first loop will cause the node to start its own ring
      PastryNode node = factory.newNode(bootHandle);
        
      // the node may require sending several messages to fully boot into the ring
      while(!node.isReady()) {
        // delay so we don't busy-wait
        Thread.sleep(100);
      }
      
      System.out.println("Finished creating new node: "+node);
      
      // construct a new MyApp
      MyScribeClient app = new MyScribeClient(node);      
      apps.add(app);
    }
    
    // for the first app subscribe then start the publishtask
    Iterator i = apps.iterator();    
    MyScribeClient app = (MyScribeClient)i.next();
    app.subscribe();
    app.startPublishTask();
    // for all the rest just subscribe
    while(i.hasNext()) {
      app = (MyScribeClient)i.next();
      app.subscribe();
    }
    
    // now, print the tree
    Thread.sleep(5000);
    printTree(apps);  
  }
  
  /**
   * Note that this function only works because we have global knowledge.  Doing 
   * this in an actual distributed environment will take some more work.
   * 
   * @param apps Vector of the applicatoins.
   */
  public static void printTree(Vector apps) {
    // build a hashtable of the apps, keyed by nodehandle
    Hashtable appTable = new Hashtable();
    Iterator i = apps.iterator();
    while (i.hasNext()) {
      MyScribeClient app = (MyScribeClient)i.next();
      appTable.put(app.endpoint.getLocalNodeHandle(), app);
    }
    NodeHandle seed = ((MyScribeClient)apps.get(0)).endpoint.getLocalNodeHandle();
    
    // get the root     
    NodeHandle root = getRoot(seed, appTable);
    
    // print the tree from the root down
    recursivelyPrintChildren(root, 0, appTable);    
  }
  
  /**
   * Recursively crawl up the tree to find the root.
   */
  public static NodeHandle getRoot(NodeHandle seed, Hashtable appTable) {
    MyScribeClient app = (MyScribeClient)appTable.get(seed);
    if (app.isRoot()) return seed;
    NodeHandle nextSeed = app.getParent();
    return getRoot(nextSeed, appTable);
  }

  /**
   * Print's self, then children.
   */
  public static void recursivelyPrintChildren(NodeHandle curNode, int recursionDepth, Hashtable appTable) {
    // print self at appropriate tab level
    String s = "";
    for (int numTabs = 0; numTabs < recursionDepth; numTabs++) {
      s+="  "; 
    }
    s+=curNode.getId().toString();
    System.out.println(s);
    
    // recursively print all children
    MyScribeClient app = (MyScribeClient)appTable.get(curNode);
    NodeHandle[] children = app.getChildren();
    for (int curChild = 0; curChild < children.length; curChild++) {
      recursivelyPrintChildren(children[curChild], recursionDepth+1, appTable);
    }    
  }
  
  /**
   * Usage: 
   * java [-cp FreePastry-<version>.jar] rice.tutorial.lesson6.ScribeTutorial localbindport bootIP bootPort numNodes
   * example java rice.tutorial.DistTutorial 9001 pokey.cs.almamater.edu 9001
   */
  public static void main(String[] args) throws Exception {
    try {
      // the port to use locally
      int bindport = Integer.parseInt(args[0]);
      
      // build the bootaddress from the command line args
      InetAddress bootaddr = InetAddress.getByName(args[1]);
      int bootport = Integer.parseInt(args[2]);
      InetSocketAddress bootaddress = new InetSocketAddress(bootaddr,bootport);
  
      // the port to use locally
      int numNodes = Integer.parseInt(args[3]);    
      
      // launch our node!
      ScribeTutorial dt = new ScribeTutorial(bindport, bootaddress, numNodes);
    } catch (Exception e) {
      // remind user how to use
      System.out.println("Usage:"); 
      System.out.println("java [-cp FreePastry-<version>.jar] rice.tutorial.lesson6.ScribeTutorial localbindport bootIP bootPort numNodes");
      System.out.println("example java rice.tutorial.DistTutorial 9001 pokey.cs.almamater.edu 9001 10");
      throw e; 
    }
  }
}
