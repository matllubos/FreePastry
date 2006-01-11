/*
 * Created on Apr 6, 2005
 */
package rice.testing.routeconsistent;

import java.io.*;
import java.net.*;
import java.nio.channels.*;
import java.util.*;

import rice.environment.Environment;
import rice.p2p.splitstream.ChannelId;
import rice.p2p.splitstream.testing.MySplitStreamClient;
import rice.pastry.*;
import rice.pastry.leafset.LeafSet;
import rice.pastry.socket.*;
import rice.pastry.standard.RandomNodeIdFactory;

/**
 * @author Jeff Hoye
 */
public class ConsistencyPLTest /*implements Observer*/ {
  public static final int startPort = 12000;
  public static final int WAIT_TO_SUBSCRIBE_DELAY = 60000;
  
  public static final boolean useScribe = false;
  public static final boolean useSplitStream = true;
  public static String INSTANCE = "ConsPLSplitStreamTest";

  public static final boolean artificialChurn = true;
  
  //the object is just to implement the destruction policy.
  PastryNode localNode;
  LeafSet leafSet;

  /**
   * Of InetSocketAddress
   */
  static HashSet bootAddresses = new HashSet();
  
  public ConsistencyPLTest(PastryNode localNode, LeafSet leafSet) {
    this.localNode = localNode;
    this.leafSet = leafSet;
//    leafSet.addObserver(this);
  }
  
//  public void update(Observable arg0, Object arg1) {
//    NodeSetUpdate nsu = (NodeSetUpdate) arg1;
//    if (!nsu.wasAdded()) {
//      if (localNode.isReady() && !leafSet.isComplete()
//          && leafSet.size() < (leafSet.maxSize() / 2)) {
//        // kill self
//        System.out.println("ConsistencyPLTest: "
//            + localNode.getEnvironment().getTimeSource().currentTimeMillis()
//            + " Killing self due to leafset collapse. " + leafSet);
//        System.exit(24);
//      }
//    }
//  }

  private static Id generateId() {
    byte[] data = new byte[20];
    new Random(100).nextBytes(data);
    return rice.pastry.Id.build(data);
  }
  
  static class BooleanHolder {
    public boolean running = true; 
  }
  
  public static void main(String[] args) throws Exception {
    
    PrintStream ps = new PrintStream(new FileOutputStream("log4.txt", true));
    System.setErr(ps);
    System.setOut(ps);

    boolean isBootNode = false;
    InetAddress localAddress = InetAddress.getLocalHost();
    if (localAddress.getHostName().startsWith("ricepl-1")) {
      isBootNode = true;
    }
    System.out.println("Ricenode:"+isBootNode);

    {
      InetAddress bootaddr;
      // build the bootaddress from the command line args
      if (args.length > 1) {
        bootaddr = InetAddress.getByName(args[1]); 
      } else {
        // this code makes ricepl-1 try to boot off of ricepl-3
        // everyone else boots off of ricepl-1
        if (isBootNode) {
          bootaddr = InetAddress.getByName("ricepl-2.cs.rice.edu"); 
        } else {
          bootaddr = InetAddress.getByName("ricepl-1.cs.rice.edu");
        }
      }
      int bootport = startPort;
      if (args.length > 2) {
        bootport = Integer.parseInt(args[2]);
      }
      InetSocketAddress bootaddress = new InetSocketAddress(bootaddr,bootport);
      bootAddresses.add(bootaddress);
      
    }
    
    while(true) { // to allow churn
      final Environment env = new Environment();
      
      System.out.println("BOOTUP:"+env.getTimeSource().currentTimeMillis());
      System.out.println("Ping Neighbor Period:"+env.getParameters().getInt("pastry_protocol_periodicLeafSet_ping_neighbor_period"));
      
      final BooleanHolder imaliveRunning = new BooleanHolder();
      new Thread(new Runnable() {
        public void run() {
          while(imaliveRunning.running) {
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
      
      // test port bindings before proceeding
      int tries = 0;
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
          tries++;
          if (tries > 100) {
            System.out.println("Too many bind attempts, shutting down.");
            System.exit(25);
          }
        }
      }
      
      // Generate the NodeIds Randomly
      NodeIdFactory nidFactory = new RandomNodeIdFactory(env);
      
      // construct the PastryNodeFactory, this is how we use rice.pastry.socket
      PastryNodeFactory factory = new SocketPastryNodeFactory(nidFactory, bindport, env);
  
      InetSocketAddress[] bootAddressCandidates = (InetSocketAddress[])bootAddresses.toArray(new InetSocketAddress[0]);
      // This will return null if we there is no node at that location
      NodeHandle bootHandle = ((SocketPastryNodeFactory)factory).getNodeHandle(bootAddressCandidates, 30000);
      
      if (bootHandle == null) {
        if (isBootNode) {
          // go ahead and start a new ring
        } else {
          // don't boot your own ring unless you are ricepl-1
          System.out.println("Couldn't find bootstrap... exiting.");        
          break; // restart join process
        }
      }
      
      // construct a node, passing the null boothandle on the first loop will cause the node to start its own ring
      final PastryNode node = factory.newNode(bootHandle);
      
      Thread shutdownHook = new Thread() {
        public void run() { System.out.println("SHUTDOWN "+env.getTimeSource().currentTimeMillis()+" "+node); }
      };
      
      Runtime.getRuntime().addShutdownHook(shutdownHook);
      
      final LeafSet ls = node.getLeafSet();
      new ConsistencyPLTest(node, ls);
      
      
      System.out.println("STARTUP "+env.getTimeSource().currentTimeMillis()+" "+node);    
      
      NodeSetListener preObserver = 
        new NodeSetListener() {
          public void nodeSetUpdate(NodeSetEventSource set, NodeHandle handle, boolean added) {
            System.out.println("LEAFSET4:"+env.getTimeSource().currentTimeMillis()+":"+ls);
            bootAddresses.add(((SocketNodeHandle)handle).getAddress());
          }
        };
      ls.addNodeSetListener(preObserver);  
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
      ls.deleteNodeSetListener(preObserver);
  
      ls.addNodeSetListener(new NodeSetListener() {
        public void nodeSetUpdate(NodeSetEventSource set, NodeHandle handle, boolean added) {
          System.out.println("LEAFSET1:"+env.getTimeSource().currentTimeMillis()+":"+ls);
          bootAddresses.add(((SocketNodeHandle)handle).getAddress());
        }
      });
  
      if (useScribe) {
        // this is to do scribe stuff
        MyScribeClient app = new MyScribeClient(node);      
        app.subscribe();
        if (isBootNode) {
          app.startPublishTask(); 
        }
      }
      
      if (useSplitStream) {
        MySplitStreamClient app = new MySplitStreamClient(node, INSTANCE);      
        ChannelId CHANNEL_ID = new ChannelId(generateId());    
        app.attachChannel(CHANNEL_ID);
        
//        if (!isBootNode) {
//          System.out.println("Sleeping(2) for "+WAIT_TO_SUBSCRIBE_DELAY+" at "+env.getTimeSource().currentTimeMillis());
//          Thread.sleep(WAIT_TO_SUBSCRIBE_DELAY);
//          System.out.println("Done(2) sleeping at "+env.getTimeSource().currentTimeMillis());
//        }   
        
        app.subscribeToAllChannels();    
        app.startPublishTask(); 
      }  
      // this is to cause different connections to open
      // TODO: Implement
      
      int maxLeafsetSize = ls.getUniqueCount();
      boolean running = true;
      while(running) {
        System.out.println("LEAFSET2:"+env.getTimeSource().currentTimeMillis()+":"+ls);
        Thread.sleep(1*60*1000);
        if (artificialChurn) {
          if (!isBootNode) {            
            
            if (env.getRandomSource().nextInt(60) == 0 || // kill self to cause churn
                (maxLeafsetSize > 8 && ls.getUniqueCount() < 4)) { // kill self because of leafset collapse
              imaliveRunning.running = false;
              Runtime.getRuntime().removeShutdownHook(shutdownHook);
              System.out.println("Killing self to cause churn. "+env.getTimeSource().currentTimeMillis()+":"+node+":"+ls);
              System.out.println("SHUTDOWN "+env.getTimeSource().currentTimeMillis()+" "+node);
              //              System.exit(25);
              node.destroy();
              env.destroy();
              running = false;
              int waittime = env.getRandomSource().nextInt(300000);
              System.out.println("Waiting for "+waittime+" millis before restarting.");
              Thread.sleep(waittime); // wait up to 5 minutes
            }
          }
        }
      }    
    }
  }  
}
