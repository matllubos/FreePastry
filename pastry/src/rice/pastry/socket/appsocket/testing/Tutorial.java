package rice.pastry.socket.appsocket.testing;

import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
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
import rice.pastry.socket.appsocket.*;
import rice.pastry.standard.RandomNodeIdFactory;

/**
 * This tutorial shows how to setup a FreePastry node using the Socket Protocol.
 * 
 * @author Jeff Hoye
 */
public class Tutorial {
  
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
  public Tutorial(int bindport, InetSocketAddress bootaddress, Environment env) throws Exception {
    
    // Generate the NodeIds Randomly
    NodeIdFactory nidFactory = new RandomNodeIdFactory(env);
    
    // construct the PastryNodeFactory
    PastryNodeFactory factory;
    factory = new SocketPastryNodeFactory(nidFactory, bindport, env);
    
    IdFactory idFactory = new PastryIdFactory(env);
    
    NodeHandle bootHandle = null;
    
      // This will return null if we there is no node at that location
  
      // construct a node, passing the null boothandle on the first loop will cause the node to start its own ring
      PastryNode node = factory.newNode(bootHandle);
      if (bootHandle == null) {
        // This will return null if we there is no node at that location
        bootHandle = ((SocketPastryNodeFactory)factory).getNodeHandle(bootaddress);
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
      
      // construct a new MyApp
      Echo app = new Echo(node, idFactory);      
      
      apps.add(app);
      
  }

  public static void main(String[] args) throws Exception {
    
      // Loads pastry settings
      Environment env;
      env = new Environment(); 
        
      // disable the UPnP setting (in case you are testing this on a NATted LAN)
      env.getParameters().setString("nat_search_policy","never");      
    
      int bindport = 9876;
      InetSocketAddress bootaddress = null;
      
      // the port to use locally
      if (args.length > 0)
        bindport = Integer.parseInt(args[0]);
      
      // build the bootaddress from the command line args
      InetAddress bootaddr = InetAddress.getLocalHost(); //ByName(args[1]);
      int bootport = bindport;
      bootaddress = new InetSocketAddress(bootaddr,bootport);    
      
      // launch our node!
      Tutorial dt = new Tutorial(bindport, bootaddress, env);
      
      BlockingAppSocketFactory asf = new BlockingAppSocketFactory();
      SocketChannel sc = asf.connect(bootaddress, -1050614594);
      sc.write(ByteBuffer.wrap("foo".getBytes()));
      System.out.println("Connected");
      
      
      try {
        asf.connect(bootaddress, 123);
      } catch (Exception e) {
        System.out.println(e); 
      }
      
      try {
        InetSocketAddress bogus = new InetSocketAddress("123.45.6.78",56);
        asf.connect(bogus, 123);
      } catch (Exception e) {
        System.out.println(e); 
      }
      
  }
}
