package rice.pastry.testing;

import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.*;

import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.p2p.commonapi.*;
import rice.p2p.commonapi.RouteMessage;
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
import rice.tutorial.direct.MyMsg;

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
  public static boolean useScribe = false;
  
  public static int rtMaintInterval = 15*60; // seconds
  public static int msgSendRate = 10000; // millis per node
  
  public static final boolean logHeavy = false;
  
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
                    testRoutingTables();
                    testRoutingTables2();
//                    if (testRoutingTables() == 0) {
//                      System.out.println("Shutting down");
//                      env.destroy();
//                    }
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
  
  private void testLeafSets() {
    if (!logHeavy) return;
    ArrayList nds = new ArrayList(nodes);
    Collections.sort(nds,new Comparator() {
    
      public int compare(Object one, Object two) {
        PastryNode n1 = (PastryNode)one;
        PastryNode n2 = (PastryNode)two;
        return n1.getId().compareTo(n2.getId());
      }
    
    });
    
    Iterator i = nds.iterator();
    while(i.hasNext()) {
      PastryNode n = (PastryNode)i.next(); 
      System.out.println(n.getLeafSet());
    }
  }
  
  private double testRoutingTables() {
//    testLeafSets();
    
    // for each node
    Iterator nodeIterator = nodes.iterator();
    int curNodeIndex = 0;
    int ctr = 0;
    double acc = 0;
    
    while(nodeIterator.hasNext()) {
      PastryNode node = (PastryNode)nodeIterator.next();
      DirectPastryNode temp = DirectPastryNode.setCurrentNode((DirectPastryNode)node);
      RoutingTable rt = node.getRoutingTable();
      Iterator i2 = nodes.iterator();
      while(i2.hasNext()) {
        PastryNode that = (PastryNode)i2.next();
        if (that != node) {
          NodeHandle thatHandle = that.getLocalHandle();        
          int latency = calcLatency(node,thatHandle);
          int proximity = node.proximity(thatHandle);
          if (latency < proximity-3) {
            calcLatency(node, thatHandle); 
          }
          double streatch = (1.0*latency)/(1.0*proximity);
//          System.out.println(streatch);
          acc+=streatch;
          ctr++;
        }
      }
      DirectPastryNode.setCurrentNode(temp);
      curNodeIndex++;
    }    
    System.out.println("Time "+env.getTimeSource().currentTimeMillis()+" = "+(acc/ctr));
    return acc/ctr;
  }

  // recursively calculate the latency
  private int calcLatency(PastryNode node, NodeHandle thatHandle) {
    DirectPastryNode temp = DirectPastryNode.setCurrentNode((DirectPastryNode)node);    
    try {
      RoutingTable rt = node.getRoutingTable();
      LeafSet ls = node.getLeafSet();
      thePenalty = 0;
      NodeHandle next = getNextHop(rt, ls, thatHandle, node);
      int penalty = thePenalty;
//      if (penalty > 0) System.out.println("penalty "+thePenalty);
      if (next == thatHandle) return node.proximity(thatHandle);  // base case
      DirectNodeHandle dnh = (DirectNodeHandle)next;    
      PastryNode nextNode = dnh.getRemote();
      return penalty+nextNode.proximity(next)+calcLatency(nextNode, thatHandle); // recursive case
    } finally {
      DirectPastryNode.setCurrentNode(temp); 
    }
  }
  
  int thePenalty = 0;  // the penalty for trying non-alive nodes
  private NodeHandle getNextHop(RoutingTable rt, LeafSet ls, NodeHandle thatHandle, PastryNode localNode) {
    rice.pastry.Id target = (rice.pastry.Id)thatHandle.getId();

    int cwSize = ls.cwSize();
    int ccwSize = ls.ccwSize();

    int lsPos = ls.mostSimilar(target);

    if (lsPos == 0) // message is for the local node so deliver it
      throw new RuntimeException("can't happen");

    else if ((lsPos > 0 && (lsPos < cwSize || !ls.get(lsPos).getNodeId()
        .clockwise(target)))
        || (lsPos < 0 && (-lsPos < ccwSize || ls.get(lsPos).getNodeId()
            .clockwise(target)))) {

    // the target is within range of the leafset, deliver it directly    
      NodeHandle handle = ls.get(lsPos);

      if (handle.isAlive() == false) {
        // node is dead - get rid of it and try again
        thePenalty += localNode.proximity(handle)*4; // rtt*2
        LeafSet ls2 = ls.copy();
        ls2.remove(handle);
        return getNextHop(rt, ls2, thatHandle, localNode);
      } else {
        return handle;
      }
    } else {
      // use the routing table
      RouteSet rs = rt.getBestEntry(target);
      NodeHandle handle = null;

      // apply penalty if node was not alive
      NodeHandle notAlive = null;
      if (rs != null
          && ((notAlive = rs.closestNode(10)) != null)) {
        if ((notAlive != null) && !notAlive.isAlive()) thePenalty+=localNode.proximity(notAlive)*4;
      }
      
      if (rs == null
          || ((handle = rs.closestNode(NodeHandle.LIVENESS_ALIVE)) == null)) {

        // penalize for choosing dead route
        NodeHandle notAlive2 = null;
        notAlive2 = rt.bestAlternateRoute(10,
            target);
        if (notAlive2 == notAlive) {
          // don't doublePenalize 
        } else {
          if ((notAlive2 != null) && !notAlive2.isAlive()) thePenalty+=localNode.proximity(notAlive2)*4;
        }
        
        // no live routing table entry matching the next digit
        // get best alternate RT entry
        handle = rt.bestAlternateRoute(NodeHandle.LIVENESS_ALIVE,
            target);

        if (handle == null) {
          // no alternate in RT, take leaf set extent
          handle = ls.get(lsPos);

          if (handle.isAlive() == false) {
            thePenalty += localNode.proximity(handle)*4;
            LeafSet ls2 = ls.copy();
            ls2.remove(handle);
            return getNextHop(rt, ls2, thatHandle, localNode);
          }
        } else {
          Id.Distance altDist = handle.getNodeId().distance(target);
          Id.Distance lsDist = ls.get(lsPos).getNodeId().distance(
              target);

          if (lsDist.compareTo(altDist) < 0) {
            // closest leaf set member is closer
            handle = ls.get(lsPos);

            if (handle.isAlive() == false) {
              thePenalty += localNode.proximity(handle)*4;
              LeafSet ls2 = ls.copy();
              ls2.remove(handle);
              return getNextHop(rt, ls2, thatHandle, localNode);
            }
          }
        }
      } //else {
        // we found an appropriate RT entry, check for RT holes at previous node
//      checkForRouteTableHole(msg, handle);
//      }

      return handle;    
    }
  }
  
  private int testRoutingTables2() {
    testLeafSets();
    
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

  public class MyApp implements Application {
    /**
     * The Endpoint represents the underlieing node.  By making calls on the 
     * Endpoint, it assures that the message will be delivered to a MyApp on whichever
     * node the message is intended for.
     */
    protected Endpoint endpoint;
    
    /**
     * The node we were constructed on.
     */
    protected Node node;

    public MyApp(Node node) {
      // We are only going to use one instance of this application on each PastryNode
      this.endpoint = node.buildEndpoint(this, "myinstance");
      
      this.node = node;
          
      // now we can receive messages
      this.endpoint.register();
    }

    /**
     * Getter for the node.
     */
    public Node getNode() {
      return node;
    }
    
    /**
     * Called to route a message to the id
     */
    public void routeMyMsg(Id id) {
      if (logHeavy)
        System.out.println(this+" sending to "+id);    
      Message msg = new MyMsg(endpoint.getId(), id);
      endpoint.route(id, msg, null);
    }
    
    /**
     * Called to directly send a message to the nh
     */
    public void routeMyMsgDirect(NodeHandle nh) {
      if (logHeavy)
        System.out.println(this+" sending direct to "+nh);    
      Message msg = new MyMsg(endpoint.getId(), nh.getId());
      endpoint.route(null, msg, nh);
    }
      
    /**
     * Called when we receive a message.
     */
    public void deliver(Id id, Message message) {
      if (logHeavy)
        System.out.println(this+" received "+message);
    }

    /**
     * Called when you hear about a new neighbor.
     * Don't worry about this method for now.
     */
    public void update(rice.p2p.commonapi.NodeHandle handle, boolean joined) {
    }
    
    /**
     * Called a message travels along your path.
     * Don't worry about this method for now.
     */
    @SuppressWarnings("deprecation")
    public boolean forward(RouteMessage message) {
      if (logHeavy)
        System.out.println(this+"forwarding "+message.getMessage());
      return true;
    }
    
    public String toString() {
      return "MyApp "+endpoint.getId();
    }

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

//    int[] churnMatrix = {0,15,60,600}; // minutes
//    
//    for (int churnIndex = 0; churnIndex < churnMatrix.length; churnIndex++) {
//      int churnTime = churnMatrix[churnIndex]*1000;
//      for (int test = 0; test < 10; test++) {      
//        switch(test) {
//          case 0: // nothing            
//            useMaintenance = false;
//            useMessaging = false;
//            useScribe = false;
//            break;
//          case 1: // maintenance normal
//            useMaintenance = true;
//            rtMaintInterval = 900;
//            useMessaging = false;
//            useScribe = false;
//          case 2: // maintenance high
//            useMaintenance = true;
//            rtMaintInterval = 60;
//            useMessaging = false;
//            useScribe = false;
//          case 3: // messaging light
//            useMaintenance = false;
//            useMessaging = true;
//            msgSendRate = 10000;
//            useScribe = false;            
//          case 4: // messaging heavy
//            useMaintenance = false;
//            useMessaging = true;
//            msgSendRate = 1000;
//            useScribe = false;                        
//          case 5: // both light
//            useMaintenance = true;
//            rtMaintInterval = 900;
//            useMessaging = true;
//            msgSendRate = 10000;
//            useScribe = false;            
//          case 6: // scribe light
//            useMaintenance = false;
//            useMessaging = false;
//            msgSendRate = 10000;
//            useScribe = true;            
//          case 7: // scribe heavy
//            useMaintenance = false;
//            useMessaging = false;
//            msgSendRate = 1000;
//            useScribe = true;                        
//          case 8: // scribe+maint
//            useMaintenance = true;
//            rtMaintInterval = 900;
//            useMessaging = false;
//            msgSendRate = 10000;
//            useScribe = true;            
//        }
//        
//        for (numNodes = 10; numNodes < Math.pow(10,5)+1; numNodes*=10) {
          for (int tries = 0; tries < 1; tries++) {
            // Loads pastry settings, and sets up the Environment for simulation
            Environment env = Environment.directEnvironment(tries+randSeed);
      //      Environment env = new Environment();
            
            if (logHeavy) {
              env.getParameters().setInt("rice.pastry.standard.ConsistentJoinProtocol_loglevel",Logger.FINE); 
              env.getParameters().setInt("rice.pastry.standard.StandardRouteSetProtocol_loglevel",405); 
              env.getParameters().setInt("rice.pastry.standard.StandardRouter_loglevel", Logger.FINE); 
            }
            
            // launch our node!
            RoutingTableTest dt = new RoutingTableTest(numNodes, numKill, env);
          } // tries
//        } // numNodes
//      } // test
//    } // churn
  }
}
