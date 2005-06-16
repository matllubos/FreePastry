/*
 * Created on Apr 6, 2005
 */
package rice.testing.routeconsistent;

import java.io.*;
import java.net.*;
import java.nio.channels.*;
import java.util.*;

import rice.environment.Environment;
import rice.pastry.*;
import rice.pastry.leafset.LeafSet;
import rice.pastry.socket.SocketPastryNodeFactory;
import rice.pastry.standard.*;
import rice.pastry.standard.RandomNodeIdFactory;

/**
 * @author Jeff Hoye
 */
public class ConsistencyPLTest implements Observer {
  public static final boolean useScribe = false;
  public static final boolean artificialChurn = !useScribe;
  public static final int startPort = 12000;
  
  //the object is just to implement the destruction policy.
  PastryNode localNode;
  LeafSet leafSet;

  public ConsistencyPLTest(PastryNode localNode, LeafSet leafSet) {
    this.localNode = localNode;
    this.leafSet = leafSet;
    leafSet.addObserver(this);
  }
  
  public void update(Observable arg0, Object arg1) {
    NodeSetUpdate nsu = (NodeSetUpdate) arg1;
    if (!nsu.wasAdded()) {
      if (localNode.isReady() && !leafSet.isComplete()
          && leafSet.size() < (leafSet.maxSize() / 2)) {
        // kill self
        System.out.println("ConsistencyPLTest: "
            + localNode.getEnvironment().getTimeSource().currentTimeMillis()
            + " Killing self due to leafset collapse. " + leafSet);
        System.exit(24);
      }
    }
  }

  
  public static void main(String[] args) throws Exception {
    
    PrintStream ps = new PrintStream(new FileOutputStream("log4.txt", true));
    System.setErr(ps);
    System.setOut(ps);

    final Environment env = new Environment();
    
    System.out.println("BOOTUP:"+env.getTimeSource().currentTimeMillis());
    System.out.println("Ping Neighbor Period:"+env.getParameters().getInt("pastry_protocol_periodicLeafSet_ping_neighbor_period"));
    boolean riceNode = false;
    InetAddress localAddress = InetAddress.getLocalHost();
    if (localAddress.getHostName().startsWith("ricepl-1")) {
      riceNode = true;
    }
    System.out.println("Ricenode:"+riceNode);
    
    new Thread(new Runnable() {
      public void run() {
        while(true) {
          System.out.println("ImALIVE:"+env.getTimeSource().currentTimeMillis());
          try {
            Thread.sleep(1000);
          } catch (Exception e) {}
        } 
      }
    },"ImALIVE").start();
    
    // the port to use locally    
    int bindport = startPort;
    if (args.length > 0) {
      bindport = Integer.parseInt(args[0]);
    }
    // todo, test port bindings before proceeding
    boolean success = false;
    while(!success) {
      try {
        InetSocketAddress bindAddress = new InetSocketAddress(InetAddress.getLocalHost(),bindport);
        
        // udp test
        DatagramChannel channel = DatagramChannel.open();
        channel.configureBlocking(false);
        channel.socket().bind(bindAddress);
        channel.close();
        
        ServerSocketChannel channel1 = ServerSocketChannel.open();
        channel1.configureBlocking(false);
        channel1.socket().bind(bindAddress);
        channel1.close();
        
        success = true;
      } catch (Exception e) {
        System.out.println("Couldn't bind on port "+bindport+" trying "+(bindport+1));
        bindport++; 
        
      }
    }
    
    
    // build the bootaddress from the command line args
    InetAddress bootaddr;
    if (args.length > 1) {
      bootaddr = InetAddress.getByName(args[1]); 
    } else {
      // this code makes ricepl-1 try to boot off of ricepl-3
      // everyone else boots off of ricepl-1
      if (riceNode) {
        bootaddr = InetAddress.getByName("ricepl-3.cs.rice.edu"); 
      } else {
        bootaddr = InetAddress.getByName("ricepl-1.cs.rice.edu");
      }
    }
    
    int bootport = startPort;
    if (args.length > 2) {
      bootport = Integer.parseInt(args[2]);
    }
    InetSocketAddress bootaddress = new InetSocketAddress(bootaddr,bootport);

    
    // Generate the NodeIds Randomly
    NodeIdFactory nidFactory = new RandomNodeIdFactory(env);
    
    // construct the PastryNodeFactory, this is how we use rice.pastry.socket
    PastryNodeFactory factory = new SocketPastryNodeFactory(nidFactory, bindport, env);

    // This will return null if we there is no node at that location
    NodeHandle bootHandle = ((SocketPastryNodeFactory)factory).getNodeHandle(bootaddress);
    
    if (bootHandle == null) {
      if (riceNode) {
        // go ahead and start a new ring
      } else {
        // don't boot your own ring unless you are ricepl-1
        System.out.println("Couldn't find bootstrap... exiting.");        
        System.exit(23); 
      }
    }
    
    // construct a node, passing the null boothandle on the first loop will cause the node to start its own ring
    final PastryNode node = factory.newNode(bootHandle);
    
    Runtime.getRuntime().addShutdownHook(new Thread() {
      public void run() { System.out.println("SHUTDOWN "+env.getTimeSource().currentTimeMillis()+" "+node); }
    });
    final LeafSet ls = node.getLeafSet();
    new ConsistencyPLTest(node, ls);
    
    
    System.out.println("STARTUP "+env.getTimeSource().currentTimeMillis()+" "+node);    
    
    Observer preObserver = 
      new Observer() {
        public void update(Observable arg0, Object arg1) {
          System.out.println("LEAFSET4:"+env.getTimeSource().currentTimeMillis()+":"+ls);
        }
      };
    ls.addObserver(preObserver);  
    // the node may require sending several messages to fully boot into the ring
    long lastTimePrinted = 0;
    while(!node.isReady()) {
      // delay so we don't busy-wait
      long now = env.getTimeSource().currentTimeMillis();
      if (now-lastTimePrinted > 3*60*1000) {
        System.out.println("LEAFSET5:"+env.getTimeSource().currentTimeMillis()+":"+ls);
        lastTimePrinted = now;
      }
      Thread.sleep(100);
    }
    System.out.println("SETREADY:"+env.getTimeSource().currentTimeMillis()+" "+node);
    ls.deleteObserver(preObserver);

    ls.addObserver(new Observer() {
      public void update(Observable arg0, Object arg1) {
        System.out.println("LEAFSET1:"+env.getTimeSource().currentTimeMillis()+":"+ls);
      }
    });

    if (useScribe) {
      // this is to do scribe stuff
      MyScribeClient app = new MyScribeClient(node);      
      app.subscribe();
      if (riceNode) {
        app.startPublishTask(); 
      }
    }
    
    // this is to cause different connections to open
    // TODO: Implement
    
    while(true) {
      System.out.println("LEAFSET2:"+env.getTimeSource().currentTimeMillis()+":"+ls);
      Thread.sleep(1*60*1000);
      if (artificialChurn) {
        if (!riceNode) {
          if (env.getRandomSource().nextInt(60) == 0) {
            System.out.println("Killing self to cause churn. "+env.getTimeSource().currentTimeMillis()+":"+node+":"+ls);
            System.exit(25);
          }
        }
      }
    }    
  }  
}
