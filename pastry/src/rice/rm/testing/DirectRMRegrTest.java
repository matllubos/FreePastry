package rice.rm.testing;

import rice.environment.Environment;
import rice.pastry.*;
import rice.pastry.join.*;
import rice.pastry.direct.*;
import rice.pastry.messaging.*;
import rice.pastry.security.*;
import rice.pastry.standard.*;
import rice.pastry.leafset.*;
import rice.pastry.routing.*;

import rice.rm.*;
import rice.rm.messaging.*;
import rice.rm.testing.*;

import java.util.*;
import java.io.*;
import java.security.*;

/**
 * @(#) DirectRMRegrTest.java
 * 
 * A regr test suite for Replica Manager.
 * 
 * @version $Id$
 * 
 * @author Animesh Nandi
 */
public class DirectRMRegrTest {
  private DirectPastryNodeFactory factory;

  public static NetworkSimulator simulator;

  private Vector pastryNodes;

  public Vector rmClients;

  public Hashtable nodeIdToApp;

  private Random rng;

  private int appCount = 0;

  private Vector objectKeys;

  // Total nodes in the system
  private int n = 50;

  public static boolean setupDone = false;

  // The number of iterations of node failures and joins.
  // In each iteration we fail 'concurrentFailures' number of nodes
  // and make 'concurrentJoins' number of nodes join the network.
  private int numIterations = 10;

  // The number of nodes that fail concurrently. This step of failing
  // nodes concurrently is repeated till the desired number of totalFailures
  // number of nodes has been killed.
  private int concurrentFailures = 3;

  // The number of nodes that join concurrently.
  private int concurrentJoins = 3;

  // Since we experiment with node failures and node joins, this keeps
  // track of the number of nodes currently alive in the Pastry network.
  private int nodesCurrentlyAlive = 0;

  private int numObjects = 200;

  private int numDeleted = 0;

  private int replicaFactor = RMRegrTestApp.rFactor;

  public DirectRMRegrTest(Environment env) {
    simulator = new EuclideanNetwork(env);
    factory = new DirectPastryNodeFactory(new RandomNodeIdFactory(), simulator, env);
    pastryNodes = new Vector();
    rmClients = new Vector();
    nodeIdToApp = new Hashtable();
    rng = new Random(PastrySeed.getSeed());
    objectKeys = new Vector();
  }

  private NodeHandle getBootstrap() {
    NodeHandle bootstrap = null;
    try {
      PastryNode lastnode = (PastryNode) pastryNodes.lastElement();
      bootstrap = lastnode.getLocalHandle();
    } catch (NoSuchElementException e) {
    }
    return bootstrap;
  }

  public void makeRMNode() {
    PastryNode pn = factory.newNode(getBootstrap());
    pastryNodes.addElement(pn);

    System.out.println("NewNode" + pn.getNodeId());
    Credentials cred = new PermissiveCredentials();

    DirectRMRegrTestApp rmApp = new DirectRMRegrTestApp(pn, cred, "Instance1");
    RMImpl rm = new RMImpl(pn, rmApp, RMRegrTestApp.rFactor, "Instance1");
    rmApp.m_appCount = appCount;

    nodeIdToApp.put(pn.getNodeId(), rmApp);
    rmClients.addElement(rmApp);
    appCount++;
  }

  public boolean simulate() {
    return simulator.simulate();
  }

  public static void main(String args[]) {
    int seed;

    // Setting the seed helps to reproduce the results of the run and
    // thus aids debugging incase the regression test fails.
    //seed = -1327173166 ;
    seed = (int) System.currentTimeMillis();
    PastrySeed.setSeed(seed);
    System.out.println("******************************");
    System.out.println("seed= " + seed);
    System.out.println("******************************");

    DirectRMRegrTest mt = new DirectRMRegrTest(new Environment());
    boolean ok;

    ok = mt.doTesting();

    System.out.println("****************************************");
    if (ok)
      System.out.println("RM Regression TEST - PASSED");
    else
      System.out.println("RM Regression TEST - FAILED");
    System.out.println("****************************************");
  }

  /**
   * The system of nodes is set up and the testing is performed while stepwise
   * failing the desired number of nodes.
   * 
   * @return true if all the tests PASSED
   */
  public boolean doTesting() {
    int i;
    //int numObjects = 1;
    //int numDeleted = 0;
    //int replicaFactor = DirectRMRegrTestApp.rFactor;
    int index;
    DirectRMRegrTestApp rmApp;
    Id objectKey;
    boolean passed = true;
    int pos;

    // We will first check to see if the Range calculations work fine
    // when the network size is smaller than the Replication factor
    // and when nodes should be responsible for the entire range

    // We will form a small Pastry network less than Replication factor
    for (i = 0; i < RMRegrTestApp.rFactor; i++) {
      makeRMNode();
      while (simulate())
        ;
      //System.out.println("Node" + i + "created");
    }
    while (simulate())
      ;

    // We will now check the range calculations to ensure that their
    // 'myRange' is the full Pastry ring

    for (i = 0; i < rmClients.size(); i++) {
      rmApp = (DirectRMRegrTestApp) rmClients.elementAt(i);
      boolean ok = true;
      Id ccw = rmApp.m_rm.myRange.getCCW();
      Id cw = rmApp.m_rm.myRange.getCW();
      if (rmApp.m_rm.myRange.isEmpty() || !ccw.equals(cw)) {
        ok = false;
        System.out.println("ERROR:: Node " + rmApp.m_rm.getNodeId()
            + " is not responsible for full Pastry Ring");
      }
      passed = passed & ok;

    }

    if (!passed) {
      System.out.println("");
      System.out
          .println("ERROR:: DirectRMRegrTest failed at this point itself when the network size was smaller than the replicationFactor. At this point all the nodes should be responsible for the entire Pastry ring");

      System.out.println("");
    }

    // We will form the Pastry network of all the nodes
    for (i = RMRegrTestApp.rFactor; i < n; i++) {
      makeRMNode();
      while (simulate())
        ;
      //System.out.println("Node" + i + "created");
    }
    while (simulate())
      ;
    System.out.println("All the nodes have been created");

    setupDone = true;
    nodesCurrentlyAlive = n;

    // We will now replicate 'numObjects' number of objects in the system
    index = rng.nextInt(n);
    rmApp = (DirectRMRegrTestApp) rmClients.elementAt(index);
    for (i = 0; i < numObjects; i++) {
      objectKey = generateTopicId(new String("Object" + i));
      objectKeys.add(objectKey);
      rmApp.replicate(objectKey);
      while (simulate())
        ;
    }

    // We will now remove 'numDeleted' number of objects from the system
    index = rng.nextInt(n);
    rmApp = (DirectRMRegrTestApp) rmClients.elementAt(index);
    for (i = 0; i < numDeleted; i++) {
      pos = rng.nextInt(numObjects);
      objectKey = (NodeId) objectKeys.elementAt(pos);
      rmApp.remove(objectKey);
      objectKeys.remove(pos);
      numObjects--;
      while (simulate())
        ;
    }

    for (i = 0; i < numIterations; i++) {
      //System.out.println("We will now join a few nodes");
      joinNodes(concurrentJoins);

      //System.out.println("We will now kill a few nodes");
      killNodes(concurrentFailures);

    }

    // We will now send heartbeat messages
    index = rng.nextInt(n);
    rmApp = (DirectRMRegrTestApp) rmClients.elementAt(index);
    for (i = 0; i < numObjects; i++) {
      objectKey = (NodeId) objectKeys.elementAt(i);
      rmApp.heartbeat(objectKey);
      while (simulate())
        ;
    }

    // We will now do the invariant checking
    for (i = 0; i < rmClients.size(); i++) {
      rmApp = (DirectRMRegrTestApp) rmClients.elementAt(i);
      passed = passed & rmApp.checkPassed();

    }

    // We will now invoke the periodic maintenance protocol
    System.out.println("Starting the periodic maintenance protocol:");
    for (i = 0; i < rmClients.size(); i++) {
      rmApp = (DirectRMRegrTestApp) rmClients.elementAt(i);
      rmApp.periodicMaintenance();
      while (simulate())
        ;
    }

    // We will also check to see if the m_pendingRanges hashtable is empty
    System.out.println("Checking to see if m_pendingRangesHashtable is empty:");
    for (i = 0; i < rmClients.size(); i++) {
      rmApp = (DirectRMRegrTestApp) rmClients.elementAt(i);
      if (!rmApp.m_rm.m_pendingRanges.isEmpty())
        System.out.println("Warning: m_pendingRanges hashtable is not empty");

    }

    return passed;
  }

  public Id generateTopicId(String topicName) {
    MessageDigest md = null;

    try {
      md = MessageDigest.getInstance("SHA");
    } catch (NoSuchAlgorithmException e) {
      System.err.println("No SHA support!");
    }

    md.update(topicName.getBytes());
    byte[] digest = md.digest();

    NodeId newId = NodeId.buildNodeId(digest);

    return newId;
  }

  /**
   * get authoritative information about liveness of node.
   */
  private boolean isReallyAlive(NodeId id) {
    return simulator.isAlive(id);
  }

  /**
   * murder the node. comprehensively.
   */
  private void killNode(PastryNode pn) {
    NetworkSimulator enet = (NetworkSimulator) simulator;
    enet.setAlive(pn.getNodeId(), false);
  }

  /**
   * Kills a number of randomly chosen nodes comprehensively and acoordingly
   * updates the data structures maintained by the test suite to keep track of
   * the currently active applications.
   */
  private void killNodes(int num) {
    int appcountKilled;
    Set keySet;
    Iterator it;
    NodeId key;
    int appcounter = 0;
    DirectRMRegrTestApp rmApp;
    int i;

    if (num == 0)
      return;

    System.out.println("Killing " + num + " nodes");
    for (i = 0; i < num; i++) {
      int n = rng.nextInt(pastryNodes.size());

      PastryNode pn = (PastryNode) pastryNodes.get(n);
      pastryNodes.remove(n);
      rmClients.remove(n);

      // We were maintaining a Hashtable to keep the inverse mapping
      // from the NodeId to the appCount. Now when we kill nodes we
      // have to keep the field appCount consistent with the
      // remaining applications present.

      appcountKilled = ((DirectRMRegrTestApp) nodeIdToApp.get(pn.getNodeId())).m_appCount;
      nodeIdToApp.remove(pn.getNodeId());
      keySet = nodeIdToApp.keySet();
      it = keySet.iterator();

      while (it.hasNext()) {
        key = (NodeId) it.next();
        rmApp = (DirectRMRegrTestApp) nodeIdToApp.get(key);
        appcounter = rmApp.m_appCount;
        if (appcounter > appcountKilled)
          rmApp.m_appCount--;
      }
      killNode(pn);
      System.out.println("Killed " + pn.getNodeId());
      initiateLeafSetMaintenance();
      initiateRouteSetMaintenance();

    }
    nodesCurrentlyAlive = nodesCurrentlyAlive - num;

    // We will now initiate the leafset and routeset maintenance to
    // make the presence of the newly killed nodes reflected.
    //System.out.println("Initiating leafset/routeset maintenance");
    initiateLeafSetMaintenance();
    initiateRouteSetMaintenance();

  }

  /**
   * Creates the specified number of new nodes and joins them to the existing
   * Pastry network. We also have to initiate leafset and routeset maintenance
   * to make the presence of these newly created nodes be reflected in the
   * leafsets and routesets of other nodes as required.
   */

  public void joinNodes(int num) {
    int i, j;
    DirectRMRegrTestApp rmApp;
    NodeId topicId;

    if (num == 0)
      return;

    System.out.println("Joining " + num + " nodes");

    // When we create nodes in the makeRMNode method the variable
    // appCount is assumed to be the index of next live application to
    // be created.
    appCount = nodesCurrentlyAlive;

    for (i = 0; i < num; i++) {
      makeRMNode();
      while (simulate())
        ;
    }
    while (simulate())
      ;

    nodesCurrentlyAlive = nodesCurrentlyAlive + num;

    // We will now initiate the leafset and routeset maintenance to
    // make the presence of the newly joined nodes reflected.
    //System.out.println("Initiating leafset/routeset maintenance");
    initiateLeafSetMaintenance();
    initiateRouteSetMaintenance();

    while (simulate())
      ;
  }

  /**
   * initiate leafset maintenance
   */
  private void initiateLeafSetMaintenance() {

    for (int i = 0; i < pastryNodes.size(); i++) {
      PastryNode pn = (PastryNode) pastryNodes.get(i);
      pn.receiveMessage(new InitiateLeafSetMaintenance());
      while (simulate())
        ;
    }

  }

  /**
   * initiate routing table maintenance
   */
  private void initiateRouteSetMaintenance() {

    for (int i = 0; i < pastryNodes.size(); i++) {
      PastryNode pn = (PastryNode) pastryNodes.get(i);
      pn.receiveMessage(new InitiateRouteSetMaintenance());
      while (simulate())
        ;
    }

  }

}

