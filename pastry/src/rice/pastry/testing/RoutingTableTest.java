package rice.pastry.testing;

import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.*;

import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.p2p.commonapi.Id;
import rice.pastry.NodeHandle;
import rice.pastry.NodeIdFactory;
import rice.pastry.PastryNode;
import rice.pastry.PastryNodeFactory;
import rice.pastry.direct.*;
import rice.pastry.leafset.LeafSet;
import rice.pastry.routing.*;
import rice.pastry.standard.RandomNodeIdFactory;
import rice.selector.SelectorManager;
import rice.selector.TimerTask;
import rice.tutorial.direct.MyApp;

/**
 * This tutorial shows how to setup a FreePastry node using the Socket Protocol.
 * 
 * @author Jeff Hoye
 */
public class RoutingTableTest {

  // this will keep track of our nodes
  Vector nodes = new Vector();
  
  Vector apps = new Vector();
  
  final Environment env;
  
  PastryNodeFactory factory;
  
  NodeIdFactory nidFactory;
  
  public static boolean useMaintenance = false;
  public static boolean useMessaging = false;
  
  public static int rtMaintInterval = 15*60; // seconds
  public static int msgSendRate = 10000; // millis per node
  
  public static final boolean logHeavy = true;
  
  public static int T_total = 0;
  public static int T_ctr = 0;
  public static int T_ave = 0;
  
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
  public RoutingTableTest(int numNodes, int numKill, final Environment env) throws Exception {
    this.env = env;
    
    // Generate the NodeIds Randomly
    nidFactory = new RandomNodeIdFactory(env);
    
    // construct the PastryNodeFactory, this is how we use rice.pastry.direct, with a Euclidean Network
    factory = new DirectPastryNodeFactory(nidFactory, new EuclideanNetwork(env), env);

    
    // loop to construct the nodes/apps
    createNodes(numNodes, numKill);    
  }

  class CreatorTimerTask extends TimerTask {
    int numNodes;
    int numToKill;
    public CreatorTimerTask(int numNodes, int numToKill) {
      this.numNodes = numNodes;
      this.numToKill = numToKill;
    }
    
    int ctr = 0;
    public void run() {
      try {
        createNode();
      } catch (InterruptedException ie) {
        ie.printStackTrace(); 
      }
      synchronized(this) {
        ctr++;
        if (ctr >= numNodes) {
          cancel();
          env.getSelectorManager().getTimer().schedule(new TimerTask() {          
            public void run() {
              killNodes(numToKill);
              if (logHeavy)
                env.getParameters().setInt("rice.pastry.routing.RoutingTable_loglevel", Logger.FINE);
              
              if (useMessaging) {
                env.getSelectorManager().getTimer().schedule(new TimerTask() {
                  public void run() {
                    sendSomeMessages();
                    if (testRoutingTables() == 0) {
                      System.out.println("Shutting down");
                      env.destroy();
                    }
                  }
                },msgSendRate,msgSendRate);
              } else {
                env.getSelectorManager().getTimer().schedule(new TimerTask() {
                  public void run() {
                    if (testRoutingTables() == 0) {
                      System.out.println("Shutting down");
                      env.destroy();
                    }
                  }    
                },rtMaintInterval*1000,rtMaintInterval*1000);
              }

            }          
          },100000);
          notifyAll(); 
        }
      }
    }    
  };
  
  public void createNodes(int numNodes, int numToKill) throws InterruptedException {    
    CreatorTimerTask ctt = new CreatorTimerTask(numNodes, numToKill);    
    env.getSelectorManager().getTimer().schedule(ctt,1000,1000); 
    synchronized(ctt) {
      while(ctt.ctr < numNodes) {
        ctt.wait(); 
      }
    }
  }
  
  public void sendSomeMessages() {        
    // for each app
    Iterator appIterator = apps.iterator();
    while(appIterator.hasNext()) {
      MyApp app = (MyApp)appIterator.next();
      
      // pick a key at random
      Id randId = nidFactory.generateNodeId();
      
      // send to that key
      app.routeMyMsg(randId);
    }
  }
  

  public PastryNode createNode() throws InterruptedException {
    NodeHandle bootHandle = null;
    if (nodes.size() > 0) {
      PastryNode bootNode = (PastryNode)nodes.get(env.getRandomSource().nextInt(nodes.size())); 
      bootHandle = bootNode.getLocalHandle();
    }
    // construct a node, passing the null boothandle on the first loop will cause the node to start its own ring
    PastryNode node = factory.newNode(bootHandle);
    
    // the node may require sending several messages to fully boot into the ring
//    synchronized(node) {
//      while(!node.isReady()) {
//        // delay so we don't busy-wait
//        node.wait();
////        env.getTimeSource().sleep(100);
//      }
//    }    
    if (useMaintenance)
      node.scheduleMsg(new InitiateRouteSetMaintenance(),rtMaintInterval*1000,rtMaintInterval*1000);
    
    nodes.add(node);

    System.out.println("Finished creating new node("+nodes.size()+") "+node);
    
    // construct a new MyApp
    MyApp app = new MyApp(node);
    
    apps.add(app);
    
    return node;
  }
  
  private void killNodes(int num) {
    for (int i = 0; i < num; i++) {
      int index = env.getRandomSource().nextInt(nodes.size());
      PastryNode pn = (PastryNode)nodes.remove(index);
      System.out.println("Destroying "+pn);
//      System.out.println(pn.getLocalHandle().isAlive());
      pn.destroy();
//      System.out.println(pn.getLocalHandle().isAlive());
    }
  }

//  class MyHelperRunnable implements Runnable {
//    int numFailed = -1;
//    public void run() {
//      synchronized(this) {
//        numFailed = testRoutingTablesHelper();
//        notifyAll();
//      }
//    }
//    
//  }
  
  // do this on the selector thread so the routing tables don't change while processing
//  private int testRoutingTables() throws InterruptedException {
//    MyHelperRunnable mhr = new MyHelperRunnable();
//    
//    env.getSelectorManager().invoke(mhr);
//    
//    synchronized(mhr) {
//      while(mhr.numFailed == -1) {
//        mhr.wait(); 
//      }
//    }
//    return mhr.numFailed;
//  }
  
  private int testRoutingTables() {
//    Collections.sort(nodes,new Comparator() {
//    
//      public int compare(Object one, Object two) {
//        PastryNode n1 = (PastryNode)one;
//        PastryNode n2 = (PastryNode)two;
//        return n1.getId().compareTo(n2.getId());
//      }
//    
//    });

    
    // for each node
    Iterator nodeIterator = nodes.iterator();
    int curNodeIndex = 0;
    int ctr = 0;
    int[] ctrs = new int[5];
    while(nodeIterator.hasNext()) {
      PastryNode node = (PastryNode)nodeIterator.next();
      DirectPastryNode temp = DirectPastryNode.setCurrentNode((DirectPastryNode)node);
      RoutingTable rt = node.getRoutingTable();
      Iterator i2 = nodes.iterator();
      while(i2.hasNext()) {
        PastryNode that = (PastryNode)i2.next();
        NodeHandle thatHandle = that.getLocalHandle();
        int response = rt.test(thatHandle);
        if (response > 1) {
          ctrs[response]++;
          ctr++;
          if (logHeavy)
            System.out.println(response+": ("+curNodeIndex+")"+node+" could have held "+thatHandle);    
        }
      }
      DirectPastryNode.setCurrentNode(temp);
      curNodeIndex++;
    }    
    System.out.println("Time "+env.getTimeSource().currentTimeMillis()+" = "+ctr+"   2:"+ctrs[2]+" 3:"+ctrs[3]+" 4:"+ctrs[4]);
    return ctr;
  }

  
  
  /**
   * Usage: 
   * java [-cp FreePastry-<version>.jar] rice.tutorial.lesson4.DistTutorial localbindport bootIP bootPort numNodes
   * example java rice.tutorial.DistTutorial 9001 pokey.cs.almamater.edu 9001 10
   */
  public static void main(String[] args) throws Exception {
    System.out.println("use: numNodes numKill randSeed maintInterval(sec) sendInterval(millis)");
    // the number of nodes to use
    int numNodes = 100;
    if (args.length > 0) numNodes = Integer.parseInt(args[0]);    

    int numKill = 10;
    if (args.length > 1) numKill = Integer.parseInt(args[1]);

    int randSeed = 5;
    if (args.length > 2) randSeed = Integer.parseInt(args[2]);
    
    int maintInterval = -1;
    if (args.length > 3) maintInterval = Integer.parseInt(args[3]);
    
    int sendInterval = -1;
    if (args.length > 4) sendInterval = Integer.parseInt(args[4]);
    
    if (maintInterval > 0) {
      useMaintenance = true;
      rtMaintInterval = maintInterval;
    }
    
    if (sendInterval > 0) {
      useMessaging = true;
      msgSendRate = sendInterval;
    }
    
    if (logHeavy) {
      System.setOut(new PrintStream(new FileOutputStream("rtt.txt")));
      System.setErr(System.out);
    }
    
    // Loads pastry settings, and sets up the Environment for simulation
    int tries = 1;
    for (int i = 0; i < tries; i++) {
      Environment env = Environment.directEnvironment(tries+randSeed);
      if (logHeavy) {
        env.getParameters().setInt("rice.pastry.standard.StandardRouteSetProtocol_loglevel",405); 
        env.getParameters().setInt("rice.pastry.standard.StandardRouter_loglevel", Logger.FINE); 
      }
      
      // launch our node!
      RoutingTableTest dt = new RoutingTableTest(numNodes, numKill, env);
    }
  }
}
