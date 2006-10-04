package rice.pastry.testing;

import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.*;

import rice.Destructable;
import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.p2p.commonapi.*;
import rice.p2p.commonapi.Id;
import rice.p2p.commonapi.RouteMessage;
import rice.p2p.scribe.*;
import rice.pastry.*;
import rice.pastry.NodeHandle;
import rice.pastry.commonapi.PastryIdFactory;
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
  boolean printLiveness = true;
  boolean printLeafSets = true;
  
  // this will keep track of our nodes
  Vector nodes = new Vector();
  
  Vector apps = new Vector();
  
  final Environment env;
  int numNodes;
  int meanSessionTime;
  boolean useScribe;
  int msgSendRate;
  int rtMaintTime;
  int tryNum;
  
  PastryNodeFactory factory;
  
  IdFactory idFactory;
  NodeIdFactory nidFactory;
  Topic topic;
  
  int reportRate = 60*1000; // 1 minute
//  int reportRate = 60*1000*10; // 10 minutes
//  int reportRate = 60*1000*60; // 1 hour
  
  int testTime = 60*1000*60*10; // 10 hours
//  int testTime = 60*1000*60; // 1 hour

  
//  public static boolean useMaintenance = false;
//  public static boolean useMessaging = false;
//  public static boolean useScribe = false;
//  
//  public static int rtMaintInterval = 15*60; // seconds
//  public static int msgSendRate = 10000; // millis per node
  
  public static final boolean logHeavy = false;
  
//  public static int T_total = 0;
//  public static int T_ctr = 0;
//  public static int T_ave = 0;
  
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
  public RoutingTableTest(int numNodes, int meanSessionTime, boolean useScribe, int msgSendRate, int rtMaintTime, int tryNum, final Environment env) throws Exception {
    System.out.println("numNodes:"+numNodes+" meanSessionTime:"+meanSessionTime+" scribe:"+useScribe+" msgSendRate:"+msgSendRate+" rtMaint:"+rtMaintTime+" try:"+tryNum);
    this.env = env;
    this.numNodes = numNodes;
    this.meanSessionTime = meanSessionTime;
    this.useScribe = useScribe;
    this.msgSendRate = msgSendRate;
    this.rtMaintTime = rtMaintTime;
    this.tryNum = tryNum;
    
    idFactory = new PastryIdFactory(env);
    topic = new Topic(idFactory.buildId("test"));
    
    
    // Generate the NodeIds Randomly
    nidFactory = new RandomNodeIdFactory(env);
    
    // construct the PastryNodeFactory, this is how we use rice.pastry.direct, with a Euclidean Network
    factory = new DirectPastryNodeFactory(nidFactory, new EuclideanNetwork(env), env);

    // loop to construct the nodes/apps
    createNodes(numNodes, meanSessionTime, useScribe, msgSendRate, rtMaintTime);    
    
    env.getSelectorManager().getTimer().schedule(new TimerTask() {
      public void run() {
        testRoutingTables();
      }    
    },reportRate,reportRate);

    env.getSelectorManager().getTimer().schedule(new TimerTask() {
      public void run() {
        env.destroy();
      }    
    },testTime);  

    // loop to construct the nodes/apps
//    createNodes(numNodes, meanSessionTime, useScribe, msgSendRate, rtMaintTime);    
  }

  class CreatorTimerTask extends TimerTask {
    int numNodes;
    int meanSessionTime;
    boolean useScribe;
    int msgSendRate;
    int rtMaintInterval;
    public CreatorTimerTask(int numNodes, int meanSessionTime, boolean useScribe, int msgSendRate, int rtMaintTime) {
      this.numNodes = numNodes;
      this.meanSessionTime = meanSessionTime;
      this.useScribe = useScribe;
      this.msgSendRate = msgSendRate;
      this.rtMaintInterval = rtMaintTime;
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
//          env.getSelectorManager().getTimer().schedule(new TimerTask() {          
//            public void run() {
//              killNodes(numToKill);
//              if (logHeavy)
//                env.getParameters().setInt("rice.pastry.routing.RoutingTable_loglevel", Logger.FINE);
//              
//              env.getSelectorManager().getTimer().schedule(new TimerTask() {
//                public void run() {
//                  testRoutingTables();
//                }    
//              },1000*60,1000*60);
//            }          
//          },100000);
//          notifyAll(); 
        }
      }
    }    
  }
  
  public void createNodes(int numNodes, int meanSessionTime, boolean useScribe, int msgSendRate, int rtMaint) throws InterruptedException {    
    CreatorTimerTask ctt = new CreatorTimerTask(numNodes, meanSessionTime, useScribe, msgSendRate, rtMaint);    
    env.getSelectorManager().getTimer().schedule(ctt,1000,1000); 
//    synchronized(ctt) {
//      while(ctt.ctr < numNodes) {
//        ctt.wait(); 
//      }
//    }
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
  
  public void sendSomeScribeMessages() {        
    // for each app
    Iterator appIterator = apps.iterator();
    while(appIterator.hasNext()) {
      Scribe app = (Scribe)appIterator.next();
      app.publish(topic, new TestScribeContent(topic, 0));
    }
  }
  
  /**
   * Utility class for past content objects
   *
   * @version $Id: ScribeRegrTest.java 3274 2006-05-15 16:17:47Z jeffh $
   * @author amislove
   */
  protected static class TestScribeContent implements ScribeContent {

    /**
     * DESCRIBE THE FIELD
     */
    protected Topic topic;

    /**
     * DESCRIBE THE FIELD
     */
    protected int num;

    /**
     * Constructor for TestScribeContent.
     *
     * @param topic DESCRIBE THE PARAMETER
     * @param num DESCRIBE THE PARAMETER
     */
    public TestScribeContent(Topic topic, int num) {
      this.topic = topic;
      this.num = num;
    }

    /**
     * DESCRIBE THE METHOD
     *
     * @param o DESCRIBE THE PARAMETER
     * @return DESCRIBE THE RETURN VALUE
     */
    public boolean equals(Object o) {
      if (!(o instanceof TestScribeContent)) {
        return false;
      }

      return (((TestScribeContent) o).topic.equals(topic) &&
        ((TestScribeContent) o).num == num);
    }

    /**
     * DESCRIBE THE METHOD
     *
     * @return DESCRIBE THE RETURN VALUE
     */
    public String toString() {
      return "TestScribeContent(" + topic + ", " + num + ")";
    }
  }


  public PastryNode createNode() throws InterruptedException {
    NodeHandle bootHandle = null;
    if (nodes.size() > 0) {
      PastryNode bootNode = null;
      while(bootNode == null || !bootNode.isReady()) {
        bootNode = (PastryNode)nodes.get(env.getRandomSource().nextInt(nodes.size())); 
        bootHandle = bootNode.getLocalHandle();
      }
    }
    // construct a node, passing the null boothandle on the first loop will cause the node to start its own ring
    final PastryNode node = factory.newNode(bootHandle);

    // this will add "magic" to the node such that if it is destroyed, then it will automatically create its replacement
    node.addDestructable(new Destructable() {          
      public void destroy() {
        System.out.println("Destructable called.");
        nodes.remove(node);
        try {
          createNode(); // create a new node every time we
                                // destroy one
        } catch (InterruptedException ie) {
          ie.printStackTrace();
        }              
      }          
    });

    if (printLiveness) 
      System.out.println("Creating "+node);
    
    // the node may require sending several messages to fully boot into the ring
//    synchronized(node) {
//      while(!node.isReady()) {
//        // delay so we don't busy-wait
//        node.wait();
////        env.getTimeSource().sleep(100);
//      }
//    }    
    synchronized(node) {
      if (node.isReady()) {
        finishNode(node);
      } else {
        System.out.println("Adding observer to "+node);
      node.addObserver(new Observer() {

      public void update(Observable o, Object arg) {
        System.out.println("observer.update("+arg+")");
        if (arg instanceof Boolean) {
          if (!((Boolean) arg).booleanValue()) return;
          
          node.deleteObserver(this);
          finishNode(node);
        } else if (arg instanceof JoinFailedException) {
          System.out.println("Got JoinFailedException:"+arg);
          node.destroy(); 
        }
      }// update

    });//addObserver
    //    System.out.println("Adding "+node);
    //      nodes.add(node);
      }//if
    }//synchronized
    return node;
  }
  
  public void finishNode(final PastryNode node) {
    nodes.add(node);

    if ((meanSessionTime > 0)) {
      env.getSelectorManager().getTimer().schedule(new TimerTask() {
        @Override
        public void run() {
          node.addDestructable(new Destructable() {          
            public void destroy() {
              cancel();
            }          
          });
          if (env.getRandomSource().nextInt(meanSessionTime * 2) == 0) {
            if (printLiveness)
              System.out.println("Destroying " + node);
            // if (!nodes.remove(node)) {
            // String s = "Couldn't remove "+node+" from ";
            // Iterator i = nodes.iterator();
            // while(i.hasNext()) {
            // s+="\t"+i.next()+"\n";
            // }
            //              
            // System.out.print(s);
            // }
            cancel();
            node.destroy();
          }
        }
      }, 60 * 1000, 60 * 1000);
    }
    if (msgSendRate > 0) {
      if (useScribe) {
        Scribe app = new ScribeImpl(node,"test");
        ScribeClient client = new TestScribeClient(app, topic);
        app.subscribe(topic, client);
        apps.add(app);
      } else {
        // construct a new MyApp
        MyApp app = new MyApp(node);    
        apps.add(app);
      }    
      
      env.getSelectorManager().getTimer().schedule(new TimerTask() {      
        @Override
        public void run() {
          if (useScribe) {
            sendSomeScribeMessages();
          } else {
            sendSomeMessages();
          }
        }      
      }, msgSendRate);
    }
    
    if (rtMaintTime > 0)
      node.scheduleMsg(new InitiateRouteSetMaintenance(),rtMaintTime*1000,rtMaintTime*1000);
    
    
    if (useScribe) {
      Scribe app = new ScribeImpl(node,"test");
      ScribeClient client = new TestScribeClient(app, topic);
      app.subscribe(topic, client);
      apps.add(app);
    } else {
      // construct a new MyApp
      MyApp app = new MyApp(node);    
      apps.add(app);
    }    
    if (printLiveness)
      System.out.println("Finished creating new node("+nodes.size()+") "+node+" at "+env.getTimeSource().currentTimeMillis());
 
  }
  
  private void testLeafSets() {
//    if (!logHeavy) return;
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
      System.out.println(n.isReady()+" "+n.getLeafSet());
    }
  }
  
  private void testRoutingTables() {    
    if (printLeafSets)
      testLeafSets();
    double streatch = testRoutingTables1();
    int holes = testRoutingTables2();
    System.out.println((env.getTimeSource().currentTimeMillis()/(60*1000))+","+streatch+","+holes);
  }
  
  /**
   * 
   * @return delay streatch
   */
  private double testRoutingTables1() {
    
    // for each node
    Iterator nodeIterator = nodes.iterator();
    int curNodeIndex = 0;
    int ctr = 0;
    double acc = 0;
    
    while(nodeIterator.hasNext()) {
      PastryNode node = (PastryNode)nodeIterator.next();
      RoutingTable rt = node.getRoutingTable();
      Iterator i2 = nodes.iterator();
      while(i2.hasNext()) {
        PastryNode that = (PastryNode)i2.next();
        if ((that != node) && that.isReady() && node.isReady()) {
          NodeHandle thatHandle = that.getLocalHandle();        
          int latency = calcLatency(node,thatHandle);
          int proximity = node.proximity(thatHandle);
          if (proximity == 0) {
            throw new RuntimeException("proximity zero:"+node+".proximity("+thatHandle+")"); 
          }
          if (latency < proximity) { // due to rounding error
            latency = proximity;
//            calcLatency(node, thatHandle); 
          }
          double streatch = (1.0*latency)/(1.0*proximity);
//          if (streatch < 1.0) System.out.println(streatch);
          acc+=streatch;
          ctr++;
        }
      }
      curNodeIndex++;
    }    
//    System.out.println("Time "+env.getTimeSource().currentTimeMillis()+" = "+(acc/ctr));
    return acc/ctr;
  }

  // recursively calculate the latency
  private int calcLatency(PastryNode node, NodeHandle thatHandle) {
      RoutingTable rt = node.getRoutingTable();
      LeafSet ls = node.getLeafSet();
      thePenalty = 0;
      NodeHandle next = getNextHop(rt, ls, thatHandle, node);
      int penalty = thePenalty;
//      if (penalty > 0) System.out.println("penalty "+thePenalty);
      if (next == thatHandle) return node.proximity(thatHandle);  // base case
      DirectNodeHandle dnh = (DirectNodeHandle)next;    
      PastryNode nextNode = dnh.getRemote();
      return penalty+node.proximity(next)+calcLatency(nextNode, thatHandle); // recursive case
  }
  
  int thePenalty = 0;  // the penalty for trying non-alive nodes
  private NodeHandle getNextHop(RoutingTable rt, LeafSet ls, NodeHandle thatHandle, PastryNode localNode) {
    rice.pastry.Id target = (rice.pastry.Id)thatHandle.getId();

    int cwSize = ls.cwSize();
    int ccwSize = ls.ccwSize();

    int lsPos = ls.mostSimilar(target);

    if (lsPos == 0) // message is for the local node so deliver it
      throw new RuntimeException("can't happen: probably a partition");

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
    // for each node
    Iterator nodeIterator = nodes.iterator();
    int curNodeIndex = 0;
    int ctr = 0;
    int[] ctrs = new int[5];
    while(nodeIterator.hasNext()) {      
      PastryNode node = (PastryNode)nodeIterator.next();
      if (!node.isReady()) continue;
      DirectPastryNode temp = DirectPastryNode.setCurrentNode((DirectPastryNode)node);
      RoutingTable rt = node.getRoutingTable();
      Iterator i2 = nodes.iterator();
      while(i2.hasNext()) {
        PastryNode that = (PastryNode)i2.next();
        if (!that.isReady()) continue;
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
//    System.out.println("Time "+env.getTimeSource().currentTimeMillis()+" = "+ctr+"   ENTRY_WAS_DEAD:"+ctrs[2]+" AVAILABLE_SPACE:"+ctrs[3]+" NO_ENTRIES:"+ctrs[4]);
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

  
  class TestScribeClient implements ScribeClient {

    /**
     * DESCRIBE THE FIELD
     */
    protected Scribe scribe;

    /**
     * The topic this client is listening for
     */
    protected Topic topic;

    /**
     * Whether or not this client should accept anycasts
     */
    protected boolean acceptAnycast;

    /**
     * Whether this client has had a subscribe fail
     */
    protected boolean subscribeFailed;

    /**
     * Constructor for TestScribeClient.
     *
     * @param scribe DESCRIBE THE PARAMETER
     * @param i DESCRIBE THE PARAMETER
     */
    public TestScribeClient(Scribe scribe, Topic topic) {
      this.scribe = scribe;
      this.topic = topic;
      this.acceptAnycast = false;
      this.subscribeFailed = false;
    }

    public void acceptAnycast(boolean value) {
      this.acceptAnycast = value;
    }

    /**
     * DESCRIBE THE METHOD
     *
     * @param topic DESCRIBE THE PARAMETER
     * @param content DESCRIBE THE PARAMETER
     * @return DESCRIBE THE RETURN VALUE
     */
    public boolean anycast(Topic topic, ScribeContent content) {
      return acceptAnycast;
    }

    /**
     * DESCRIBE THE METHOD
     *
     * @param topic DESCRIBE THE PARAMETER
     * @param content DESCRIBE THE PARAMETER
     */
    public void deliver(Topic topic, ScribeContent content) {

    }

    /**
     * DESCRIBE THE METHOD
     *
     * @param topic DESCRIBE THE PARAMETER
     * @param child DESCRIBE THE PARAMETER
     */
    public void childAdded(Topic topic, rice.p2p.commonapi.NodeHandle child) {
     // System.out.println("CHILD ADDED AT " + scribe.getId());
    }

    /**
     * DESCRIBE THE METHOD
     *
     * @param topic DESCRIBE THE PARAMETER
     * @param child DESCRIBE THE PARAMETER
     */
    public void childRemoved(Topic topic, rice.p2p.commonapi.NodeHandle child) {
     // System.out.println("CHILD REMOVED AT " + scribe.getId());
    }

    public void subscribeFailed(Topic topic) {
      subscribeFailed = true;
    }

    public boolean getSubscribeFailed() {
      return subscribeFailed;
    }
  }
  
  /**
   * Usage: 
   * java [-cp FreePastry-<version>.jar] rice.tutorial.lesson4.DistTutorial localbindport bootIP bootPort numNodes
   * example java rice.tutorial.DistTutorial 9001 pokey.cs.almamater.edu 9001 10
   */
  public static void main(String[] args) throws Exception {
//    System.out.println("use: numNodes numKill randSeed maintInterval(sec) sendInterval(millis)");
    // the number of nodes to use
//    int numNodes = 100;
//    if (args.length > 0) numNodes = Integer.parseInt(args[0]);    
//
//    int numKill = 10;
//    if (args.length > 1) numKill = Integer.parseInt(args[1]);
//
//    int randSeed = 5;
//    if (args.length > 2) randSeed = Integer.parseInt(args[2]);
//    
//    int maintInterval = -1;
//    if (args.length > 3) maintInterval = Integer.parseInt(args[3]);
//    
//    int sendInterval = -1;
//    if (args.length > 4) sendInterval = Integer.parseInt(args[4]);
//    
//    if (maintInterval > 0) {
//      useMaintenance = true;
//      rtMaintInterval = maintInterval;
//    }
//    
//    if (sendInterval > 0) {
//      useMessaging = true;
//      msgSendRate = sendInterval;
//    }
//    
//    if (logHeavy) {
//      System.setOut(new PrintStream(new FileOutputStream("rtt.txt")));
//      System.setErr(System.out);
//    }

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
    
    int[] rtMaintVals = {0,60,15,1};
    int[] msgSendVals = {0,10000,1000,100};
        for (int numNodes = 100; numNodes < 100001; numNodes*=10) 
        for (int useScribeIndex = 0; useScribeIndex < 2; useScribeIndex++) 
        for (int rtMaintIndex = 0; rtMaintIndex < 4; rtMaintIndex++)
        for (int msgSendRateIndex = 0; msgSendRateIndex < 4; msgSendRateIndex++)
//        for (int meanSessionTime = 1; meanSessionTime < 10001; meanSessionTime*=10)          
          for (int tries = 0; tries < 10; tries++) {
            int meanSessionTime = 1;
            boolean useScribe = true;
            if (useScribeIndex == 0) useScribe = false;
            final Object lock = new Object();
            // Loads pastry settings, and sets up the Environment for simulation
            Environment env = Environment.directEnvironment();
            env.addDestructable(new Destructable() {            
              public void destroy() {
                synchronized(lock) {
                  lock.notify();
                }
              }            
            });
            
            if (logHeavy) {
              env.getParameters().setInt("rice.pastry.standard.ConsistentJoinProtocol_loglevel",Logger.FINE); 
              env.getParameters().setInt("rice.pastry.standard.StandardRouteSetProtocol_loglevel",405); 
              env.getParameters().setInt("rice.pastry.standard.StandardRouter_loglevel", Logger.FINE); 
            }

            // launch our node!
//            public RoutingTableTest(int numNodes, int meanSessionTime, int msgSendRate, int rtMaintTime, final Environment env) throws Exception {
            RoutingTableTest dt = new RoutingTableTest(numNodes, meanSessionTime == 1 ? 0 : meanSessionTime, useScribe, msgSendVals[msgSendRateIndex], rtMaintVals[rtMaintIndex], tries, env);
            synchronized(lock) {
              lock.wait(); // will be notified when the environment is destroyed
            }
          } // tries
//        } // numNodes
//      } // test
//    } // churn
  }
}
