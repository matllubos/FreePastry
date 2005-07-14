/*
 * Created on Jul 13, 2005
 */
package rice.p2p.splitstream.testing;

import java.io.*;
import java.net.*;
import java.nio.channels.*;
import java.util.Random;

import rice.environment.Environment;
import rice.p2p.splitstream.ChannelId;
import rice.pastry.*;
import rice.pastry.socket.SocketPastryNodeFactory;
import rice.pastry.standard.RandomNodeIdFactory;

/**
 * @author Jeff Hoye
 */
public class SplitStreamDistTest {
  public static final int IM_ALIVE_PERIOD = 5000;
    
  public static String INSTANCE = "DistSplitStreamTest";

  
  /**
   * Usage java rice.p2p.splitstream.testing.SplitStreamDistTest <artificialchurn?> <bootstrap> <port> <bootport>
   * 
   * @param args
   * @throws Exception
   */
  public static void main(String[] args) throws Exception {
    
    // setup output
//    PrintStream ps = new PrintStream(new FileOutputStream("ss.txt", true));
//    System.setErr(ps);
//    System.setOut(ps);

    // setup environment
    final Environment env = new Environment();
    System.out.println("BOOTUP:"+env.getTimeSource().currentTimeMillis());
    
    
    // **************** parse args ***************
    // artificial churn
    boolean artificialChurn = false;
    if (args.length > 0) {
//      artificialChurn = Boolean.getBoolean(args[0]); 
    }
    
    // parse non automatic bootstrap
    String bootNode = "ricepl-3.cs.rice.edu";
    if (args.length > 1) {
      bootNode = args[1]; 
    }

    // parse non automatic port
    int port = 13245;
    if (args.length > 2) {
      port = Integer.parseInt(args[2]); 
    }
            
    boolean isBootNode = true;
    InetAddress localAddress = InetAddress.getLocalHost();
    if (localAddress.getHostName().startsWith(bootNode)) {
      isBootNode = true;
    }
    System.out.println("isBootNode:"+isBootNode);
    
    if (IM_ALIVE_PERIOD > 0) {
      new Thread(new Runnable() {
        public void run() {
          while(true) {
            System.out.println("ImALIVE:"+env.getTimeSource().currentTimeMillis());
            try {
              Thread.sleep(IM_ALIVE_PERIOD);
            } catch (Exception e) {}
          } 
        }
      },"ImALIVE").start();
    }
    
    // test port bindings before proceeding
    boolean success = false;
    while(!success) {
      try {
        InetSocketAddress bindAddress = new InetSocketAddress(InetAddress.getLocalHost(),port);
        
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
        System.out.println("Couldn't bind on port "+port+" trying "+(port+1));
        port++; 
        
      }
    }
    InetAddress bootaddr = InetAddress.getByName(bootNode);

    // make bootport
    int bootport = port;
    if (args.length > 3) {
      bootport = Integer.parseInt(args[3]);
    }
    InetSocketAddress bootaddress = new InetSocketAddress(bootaddr,bootport);

    // Generate the NodeIds Randomly
    NodeIdFactory nidFactory = new RandomNodeIdFactory(env);
    
    // construct the PastryNodeFactory, this is how we use rice.pastry.socket
    PastryNodeFactory factory = new SocketPastryNodeFactory(nidFactory, port, env);

    // This will return null if we there is no node at that location
    NodeHandle bootHandle = ((SocketPastryNodeFactory)factory).getNodeHandle(bootaddress);
    
    if (bootHandle == null) {
      if (isBootNode) {
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

    MySplitStreamClient app = new MySplitStreamClient(node, INSTANCE);      
    ChannelId CHANNEL_ID = new ChannelId(generateId());
    app.attachChannel(CHANNEL_ID);
    if (isBootNode) {
      app.startPublishTask(); 
    }
  
    // this is to cause different connections to open
    if (artificialChurn) {
      while(true) {
        Thread.sleep(1*60*1000);
        if (!isBootNode) {
          if (env.getRandomSource().nextInt(60) == 0) {
            System.out.println("Killing self to cause churn. "+env.getTimeSource().currentTimeMillis()+":"+node);
            System.exit(25);
          }
        }
      }
    } 
  }
  
  private static Id generateId() {
    byte[] data = new byte[20];
    new Random(100).nextBytes(data);
    return rice.pastry.Id.build(data);
  }

  
}
