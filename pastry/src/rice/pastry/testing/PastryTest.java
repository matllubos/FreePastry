package rice.pastry.testing;

import rice.environment.Environment;
import rice.environment.params.simple.SimpleParameters;
import rice.environment.time.simulated.DirectTimeSource;
import rice.pastry.*;
import rice.pastry.direct.*;
import rice.pastry.standard.*;
import rice.pastry.join.*;

import java.io.IOException;
import java.util.*;

/**
 * Pastry test.
 * 
 * a simple test for pastry.
 * 
 * @version $Id$
 * 
 * @author andrew ladd
 * @author sitaram iyer
 */

public class PastryTest {
  private DirectPastryNodeFactory factory;

  private NetworkSimulator simulator;

  private Vector pastryNodes;

  private Vector pingClients;

  private Environment environment;

  public PastryTest(Environment env) {
    environment = env;
    simulator = new EuclideanNetwork(env);
    factory = new DirectPastryNodeFactory(new RandomNodeIdFactory(env), simulator,
        env);

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

  public void makePastryNode() {
    PastryNode pn = factory.newNode(getBootstrap());
    pastryNodes.addElement(pn);

    PingClient pc = new PingClient(pn);
    pingClients.addElement(pc);
    
    synchronized (pn) {
      while(!pn.isReady()) {
        try {
          pn.wait();
        } catch (InterruptedException ie) {}
      }
    }
    
  }

  public void sendPings(int k) {
    int n = pingClients.size();

    for (int i = 0; i < k; i++) {
      int from = environment.getRandomSource().nextInt(n);
      int to = environment.getRandomSource().nextInt(n);

      PingClient pc = (PingClient) pingClients.get(from);
      PastryNode pn = (PastryNode) pastryNodes.get(to);

      pc.sendTrace(pn.getNodeId());

      while (simulate())
        ;

      System.out.println("-------------------");
    }
  }

  public boolean simulate() {
    try { Thread.sleep(300); } catch (InterruptedException ie) {}    
    return false;
//    return simulator.simulate();
  }

  public static void main(String args[]) throws IOException {
    PastryTest pt = new PastryTest(Environment.directEnvironment());

    int n = 4000;
    int m = 100;
    int k = 10;

    int msgCount = 0;

    Date old = new Date();

    for (int i = 0; i < n; i++) {
      pt.makePastryNode();
      if(i%100 == 0)
        System.out.println("Created node "+i+"/"+n);
//      while (pt.simulate())
        msgCount++;

      if ((i + 1) % m == 0) {
        Date now = new Date();
        System.out.println((i + 1) + " " + (now.getTime() - old.getTime())
            + " " + msgCount);
        msgCount = 0;
        old = now;
      }
    }

    System.out.println(n + " nodes constructed");

    pt.sendPings(k);
  }
}
