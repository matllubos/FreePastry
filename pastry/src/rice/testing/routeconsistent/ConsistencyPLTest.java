/*
 * Created on Apr 6, 2005
 */
package rice.testing.routeconsistent;

import java.io.FileOutputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.DatagramChannel;
import java.nio.channels.ServerSocketChannel;
import java.util.Observable;
import java.util.Observer;

import rice.pastry.NodeHandle;
import rice.pastry.NodeIdFactory;
import rice.pastry.PastryNode;
import rice.pastry.PastryNodeFactory;
import rice.pastry.leafset.LeafSet;
import rice.pastry.socket.SocketPastryNodeFactory;
import rice.pastry.standard.RandomNodeIdFactory;

/**
 * @author Jeff Hoye
 */
public class ConsistencyPLTest {

  public static void main(String[] args) throws Exception {
    
    PrintStream ps = new PrintStream(new FileOutputStream("log4.txt", true));
    System.setErr(ps);
    System.setOut(ps);

    System.out.println("BOOTUP:"+System.currentTimeMillis());
    
    new Thread(new Runnable() {
      public void run() {
        while(true) {
          System.out.println("ImALIVE:"+System.currentTimeMillis());
          try {
            Thread.sleep(1000);
          } catch (Exception e) {}
        } 
      }
    },"ImALIVE").start();
    
    // the port to use locally    
    int bindport = 12000;
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
      bootaddr = InetAddress.getByName("ricepl-1.cs.rice.edu");
    }
    
    int bootport = 12000;
    if (args.length > 2) {
      bootport = Integer.parseInt(args[2]);
    }
    InetSocketAddress bootaddress = new InetSocketAddress(bootaddr,bootport);

    
    // Generate the NodeIds Randomly
    NodeIdFactory nidFactory = new RandomNodeIdFactory();
    
    // construct the PastryNodeFactory, this is how we use rice.pastry.socket
    PastryNodeFactory factory = new SocketPastryNodeFactory(nidFactory, bindport);

    // This will return null if we there is no node at that location
    NodeHandle bootHandle = ((SocketPastryNodeFactory)factory).getNodeHandle(bootaddress);
      
    // construct a node, passing the null boothandle on the first loop will cause the node to start its own ring
    final PastryNode node = factory.newNode(bootHandle);
    
    Runtime.getRuntime().addShutdownHook(new Thread() {
      public void run() { System.out.println("SHUTDOWN "+System.currentTimeMillis()+" "+node); }
    });
    final LeafSet ls = node.getLeafSet();
    
    System.out.println("STARTUP "+System.currentTimeMillis()+" "+node);    
    
    Observer preObserver = 
      new Observer() {
        public void update(Observable arg0, Object arg1) {
          System.out.println("LEAFSET4:"+System.currentTimeMillis()+":"+ls);
        }
      };
    ls.addObserver(preObserver);  
    // the node may require sending several messages to fully boot into the ring
    long lastTimePrinted = 0;
    while(!node.isReady()) {
      // delay so we don't busy-wait
      long now = System.currentTimeMillis();
      if (now-lastTimePrinted > 3*60*1000) {
        System.out.println("LEAFSET5:"+System.currentTimeMillis()+":"+ls);
        lastTimePrinted = now;
      }
      Thread.sleep(100);
    }
    System.out.println("SETREADY:"+System.currentTimeMillis()+" "+node);
    ls.deleteObserver(preObserver);

    ls.addObserver(new Observer() {
      public void update(Observable arg0, Object arg1) {
        System.out.println("LEAFSET1:"+System.currentTimeMillis()+":"+ls);
      }
    });
    
    while(true) {
      System.out.println("LEAFSET2:"+System.currentTimeMillis()+":"+ls);
      Thread.sleep(3*60*1000);
    }    
  }  
}
