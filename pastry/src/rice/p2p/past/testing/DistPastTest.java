/*
 * Created on Jun 24, 2005
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package rice.p2p.past.testing;

import java.io.Serializable;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Iterator;

import rice.Continuation;
import rice.environment.Environment;
import rice.p2p.commonapi.Id;
import rice.p2p.commonapi.IdFactory;
import rice.p2p.commonapi.NodeHandle;
import rice.p2p.past.ContentHashPastContent;
import rice.p2p.past.Past;
import rice.p2p.past.PastContent;
import rice.p2p.past.PastContentHandle;
import rice.p2p.past.PastException;
import rice.p2p.past.PastImpl;
import rice.pastry.NodeIdFactory;
import rice.pastry.PastryNode;
import rice.pastry.PastryNodeFactory;
import rice.pastry.commonapi.PastryIdFactory;
import rice.pastry.leafset.LeafSet;
import rice.pastry.socket.SocketPastryNodeFactory;
import rice.pastry.standard.RandomNodeIdFactory;
import rice.persistence.LRUCache;
import rice.persistence.MemoryStorage;
import rice.persistence.PersistentStorage;
import rice.persistence.Storage;
import rice.persistence.StorageManagerImpl;

/**
 * @author jstewart
 *
 * TODO To change the template for this generated type comment go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
public class DistPastTest {

    public DistPastTest(int bindport, InetSocketAddress bootaddress, Environment env, int numNodes) throws Exception {
      
      // Generate the NodeIds Randomly
      NodeIdFactory nidFactory = new RandomNodeIdFactory(env);
      Past p = null;
      Storage stor = null;

      // used for generating PastContent object Ids.
      PastryIdFactory idf = new rice.pastry.commonapi.PastryIdFactory(env);
      
      // construct the PastryNodeFactory, this is how we use rice.pastry.socket
      PastryNodeFactory factory = new SocketPastryNodeFactory(nidFactory, bindport, env);

      // loop to construct the nodes/apps
      for (int curNode = 0; curNode < numNodes; curNode++) {
        // This will return null if we there is no node at that location
        NodeHandle bootHandle = ((SocketPastryNodeFactory)factory).getNodeHandle(bootaddress);
    
        // construct a node, passing the null boothandle on the first loop will cause the node to start its own ring
        PastryNode node = factory.newNode((rice.pastry.NodeHandle)bootHandle);
          
        // the node may require sending several messages to fully boot into the ring
        while(!node.isReady()) {
          // delay so we don't busy-wait
          Thread.sleep(100);
        }
        
        System.out.println("Finished creating new node "+node);
        
        
        stor = new PersistentStorage(idf,".",4*1024*1024,node.getEnvironment());
        	p = new PastImpl(node, new StorageManagerImpl(idf,stor,new LRUCache(new MemoryStorage(idf),512*1024,node.getEnvironment())), 3, "");
      }
        
      String s = "test" + env.getRandomSource().nextInt();
      p.insert(new DistPastTestContent(env,idf,s), new Continuation() {
        public void receiveResult(Object result) {
          System.out.println("got: "+result);
        }

        public void receiveException(Exception result) {
          result.printStackTrace();
        }
      });
      
    }

    /**
     * Usage: 
     * java [-cp FreePastry-<version>.jar] rice.tutorial.lesson4.DistTutorial localbindport bootIP bootPort numNodes
     * example java rice.tutorial.DistTutorial 9001 pokey.cs.almamater.edu 9001 10
     */
    public static void main(String[] args) throws Exception {
      // Loads pastry settings
      Environment env = new Environment();
      
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
        DistPastTest dt = new DistPastTest(bindport, bootaddress, env, numNodes);
      } catch (Exception e) {
        // remind user how to use
        System.out.println("Usage:"); 
        System.out.println("java [-cp FreePastry-<version>.jar] rice.tutorial.lesson4.DistTutorial localbindport bootIP bootPort numNodes");
        System.out.println("example java rice.tutorial.DistTutorial 9001 pokey.cs.almamater.edu 9001 10");
        throw e; 
      }
    }
  }

