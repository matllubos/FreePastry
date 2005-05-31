package rice.pastry.testing;

import rice.environment.Environment;
import rice.pastry.*;
import rice.pastry.direct.*;
import rice.pastry.standard.*;
import rice.pastry.join.*;
import rice.pastry.client.*;
import rice.pastry.messaging.*;
import rice.pastry.security.*;
import rice.pastry.routing.*;
import rice.pastry.leafset.*;

import java.util.*;

/**
 * PastryRegrTest
 * 
 * a regression test suite for pastry.
 * 
 * @version $Id$
 * 
 * @author andrew ladd
 * @author peter druschel
 * @author sitaram iyer
 */

public class DirectPastryRegrTest extends PastryRegrTest {
  private NetworkSimulator simulator;

  /**
   * constructor
   */
  private DirectPastryRegrTest() {
    super();
    simulator = new SphereNetwork();
    factory = new DirectPastryNodeFactory(new RandomNodeIdFactory(), simulator,
        new Environment());
  }

  /**
   * Get pastryNodes.last() to bootstrap with, or return null.
   */
  protected NodeHandle getBootstrap(boolean firstNode) {
    NodeHandle bootstrap = null;
    try {
      PastryNode lastnode = (PastryNode) pastryNodes.lastElement();
      bootstrap = lastnode.getLocalHandle();
    } catch (NoSuchElementException e) {
    }
    return bootstrap;
  }

  /**
   * wire protocol specific handling of the application object e.g., RMI may
   * launch a new thread
   * 
   * @param pn pastry node
   * @param app newly created application
   */
  protected void registerapp(PastryNode pn, RegrTestApp app) {
  }

  /**
   * send one simulated message
   */
  protected boolean simulate() {
    boolean res = simulator.simulate();
    if (res)
      msgCount++;
    return res;
  }

  // do nothing in the simulated world
  public void pause(int ms) {
  }

  /**
   * get authoritative information about liveness of node.
   */
  protected boolean isReallyAlive(NodeId id) {
    return simulator.isAlive(id);
  }

  /**
   * murder the node. comprehensively.
   */
  protected void killNode(PastryNode pn) {
    NetworkSimulator enet = (NetworkSimulator) simulator;
    enet.setAlive(pn.getNodeId(), false);
  }

  /**
   * main. just create the object and call PastryNode's main.
   */
  public static void main(String args[]) {
    Log.init(args);
    DirectPastryRegrTest pt = new DirectPastryRegrTest();
    mainfunc(pt, args, 1000 /* n */, 100/* d */, 10/* k */, 100/* m */, 1/* conc */);
  }
}

