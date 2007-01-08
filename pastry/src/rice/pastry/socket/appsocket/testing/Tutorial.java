/*************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate 

Copyright 2002, Rice University. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither  the name  of Rice  University (RICE) nor  the names  of its
contributors may be  used to endorse or promote  products derived from
this software without specific prior written permission.

This software is provided by RICE and the contributors on an "as is"
basis, without any representations or warranties of any kind, express
or implied including, but not limited to, representations or
warranties of non-infringement, merchantability or fitness for a
particular purpose. In no event shall RICE or contributors be liable
for any direct, indirect, incidental, special, exemplary, or
consequential damages (including, but not limited to, procurement of
substitute goods or services; loss of use, data, or profits; or
business interruption) however caused and on any theory of liability,
whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even
if advised of the possibility of such damage.

********************************************************************************/
package rice.pastry.socket.appsocket.testing;

import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Vector;

import rice.environment.Environment;
import rice.p2p.commonapi.*;
import rice.pastry.NodeHandle;
import rice.pastry.NodeIdFactory;
import rice.pastry.PastryNode;
import rice.pastry.PastryNodeFactory;
import rice.pastry.commonapi.PastryIdFactory;
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
      new Tutorial(bindport, bootaddress, env);
      
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
