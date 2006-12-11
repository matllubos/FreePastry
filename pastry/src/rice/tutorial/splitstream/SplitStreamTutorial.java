package rice.tutorial.splitstream;

import java.io.IOException;
import java.net.*;
import java.util.*;

import rice.environment.Environment;
import rice.p2p.commonapi.*;
import rice.pastry.NodeHandle;
import rice.pastry.*;
import rice.pastry.commonapi.PastryIdFactory;
import rice.pastry.direct.*;
import rice.pastry.socket.SocketPastryNodeFactory;
import rice.pastry.standard.RandomNodeIdFactory;

/**
 * This tutorial shows how to use Scribe.
 * 
 * @author Jeff Hoye
 */
public class SplitStreamTutorial {

  /**
   * this will keep track of our Scribe applications
   */
  Vector apps = new Vector();

  /**
   * Based on the rice.tutorial.lesson4.DistTutorial
   * 
   * This constructor launches numNodes PastryNodes. They will bootstrap to an
   * existing ring if one exists at the specified location, otherwise it will
   * start a new ring.
   * 
   * @param bindport the local port to bind to
   * @param bootaddress the IP:port of the node to boot from
   * @param numNodes the number of nodes to create in this JVM
   * @param env the Environment
   */
  public SplitStreamTutorial(int bindport, InetSocketAddress bootaddress,
      int numNodes, Environment env, boolean useDirect) throws Exception {
    
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
    
    NodeHandle bootHandle = null;
    
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
      synchronized(node) {
        while(!node.isReady() && !node.joinFailed()) {
          // delay so we don't busy-wait
          node.wait(500);
          
          // abort if can't join
          if (node.joinFailed()) {
            throw new IOException("Could not join the FreePastry ring.  Reason:"+node.joinFailedReason()); 
          }
        }       
      }
      
      System.out.println("Finished creating new node "+node);

      // construct a new scribe application
      
      MySplitStreamClient app = new MySplitStreamClient(node);
      apps.add(app);
    }

    // for the first app subscribe then start the publishtask
    Iterator i = apps.iterator();
    MySplitStreamClient app = (MySplitStreamClient) i.next();
    app.subscribe();
    app.startPublishTask();
    // for all the rest just subscribe
    while (i.hasNext()) {
      app = (MySplitStreamClient) i.next();
      app.subscribe();
    }

    // now, print the tree
    env.getTimeSource().sleep(5000);
  }

  /**
   * Usage: java [-cp FreePastry- <version>.jar]
   * rice.tutorial.lesson6.ScribeTutorial localbindport bootIP bootPort numNodes
   * example java rice.tutorial.DistTutorial 9001 pokey.cs.almamater.edu 9001
   */
  public static void main(String[] args) throws Exception {
    try {
      boolean useDirect;
      if (args[0].equalsIgnoreCase("-direct")) {
        useDirect = true;
      } else {
        useDirect = false; 
      }
            
      // Loads pastry settings
      Environment env;
      if (useDirect) {
        env = Environment.directEnvironment();
      } else {
        env = new Environment(); 
        
        // disable the UPnP setting (in case you are testing this on a NATted LAN)
        env.getParameters().setString("nat_search_policy","never");      
      }
    
      int bindport = 0;
      InetSocketAddress bootaddress = null;
      
      int numNodes;    
      
      if (!useDirect) {
        // the port to use locally
        bindport = Integer.parseInt(args[0]);
        
        // build the bootaddress from the command line args
        InetAddress bootaddr = InetAddress.getByName(args[1]);
        int bootport = Integer.parseInt(args[2]);
        numNodes = Integer.parseInt(args[3]);
        bootaddress = new InetSocketAddress(bootaddr,bootport);    
      } else {
        numNodes = Integer.parseInt(args[1]);
      }
      
      // launch our node!
      SplitStreamTutorial dt = new SplitStreamTutorial(bindport, bootaddress, numNodes,
          env, useDirect);
    } catch (Exception e) {
      // remind user how to use
      System.out.println("Usage:");
      System.out
          .println("java [-cp FreePastry-<version>.jar] rice.tutorial.lesson6.ScribeTutorial localbindport bootIP bootPort numNodes");
      System.out
          .println("example java rice.tutorial.DistTutorial 9001 pokey.cs.almamater.edu 9001 10");
      throw e;
    }
  }
}