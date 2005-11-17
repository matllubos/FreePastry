package rice.pastry.testing;

import rice.environment.Environment;
import rice.pastry.*;
import rice.pastry.direct.*;
import rice.pastry.standard.*;
import rice.pastry.join.*;
import rice.pastry.routing.*;

import java.util.*;
import java.io.*;
import java.lang.*;

/**
 * SinglePingTest
 * 
 * A performance test suite for pastry.
 * 
 * @version $Id$
 * 
 * @author Rongmei Zhang
 */

public class SinglePingTest {
  private DirectPastryNodeFactory factory;

  private NetworkSimulator simulator;

  private TestRecord testRecord;

  private Vector pastryNodes;

  private Vector pingClients;

  private Environment environment;

  public SinglePingTest(TestRecord tr, Environment env) {
    environment = env;
    simulator = new EuclideanNetwork(env); //SphereNetwork();
    factory = new DirectPastryNodeFactory(new RandomNodeIdFactory(environment), simulator, env);
    simulator.setTestRecord(tr);
    testRecord = tr;

    pastryNodes = new Vector();
    pingClients = new Vector();
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

  public PastryNode makePastryNode() {
    PastryNode pn = factory.newNode(getBootstrap());
    pastryNodes.addElement(pn);

    Ping pc = new Ping(pn);
    pingClients.addElement(pc);
    
    return pn;
  }

  public void sendPings(int k) {    
    int n = pingClients.size();

    for (int i = 0; i < k; i++) {
      int from = environment.getRandomSource().nextInt(n);
      int to = environment.getRandomSource().nextInt(n);

      Ping pc = (Ping) pingClients.get(from);
      PastryNode pn = (PastryNode) pastryNodes.get(to);

      pc.sendPing(pn.getNodeId());
      while (simulate());
    }
  }

  public boolean simulate() {
    return simulator.simulate();
  }

  public void checkRoutingTable() {
    int i;
    Date prev = new Date();

    for (i = 0; i < testRecord.getNodeNumber(); i++) {
      PastryNode pn = makePastryNode();
      while (simulate());
      System.out.println(pn.getLeafSet());

      if (i != 0 && i % 1000 == 0)
        System.out.println(i + " nodes constructed");
    }
    System.out.println(i + " nodes constructed");

    Date curr = new Date();
    long msec = curr.getTime() - prev.getTime();
    System.out.println("time used " + (msec / 60000) + ":"
        + ((msec % 60000) / 1000) + ":" + ((msec % 60000) % 1000));

    //	simulator.checkRoutingTable();
  }

  public void test() {
    int i;
    Date prev = new Date();

    System.out.println("-------------------------");
    for (i = 0; i < testRecord.getNodeNumber(); i++) {
      PastryNode pn = makePastryNode();
      while (simulate());
//      System.out.println(pn.getLeafSet());

      if (i != 0 && i % 500 == 0)
        System.out.println(i + " nodes constructed");
    }
    System.out.println(i + " nodes constructed");

    Date curr = new Date();
    long msec = curr.getTime() - prev.getTime();
    System.out.println("time used " + (msec / 60000) + ":"
        + ((msec % 60000) / 1000) + ":" + ((msec % 60000) % 1000));
    prev = curr;

    sendPings(testRecord.getTestNumber());
    System.out.println(testRecord.getTestNumber() + " lookups done");

    curr = new Date();
    msec = curr.getTime() - prev.getTime();
    System.out.println("time used " + (msec / 60000) + ":"
        + ((msec % 60000) / 1000) + ":" + ((msec % 60000) % 1000));

    testRecord.doneTest();
  }
}

