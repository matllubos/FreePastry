
package rice.pastry.testing;

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
 * ClosestRegrTest
 *
 * A test suite for the getClosest algorithm
 *
 * @version $Id$
 *
 * @author alan mislove
 */
public class ClosestRegrTest {

  public static int NUM_NODES = 1000;
  public static int NUM_TRIALS = 1000;

  private PastryNodeFactory factory;
  private NetworkSimulator simulator;
  private Vector pastryNodes;
  
  Random random = new Random();
  int incorrect = 0;
  double sum = 0;

  /**
   * constructor
   */
  private ClosestRegrTest() {
    simulator = new SphereNetwork();
    factory = new DirectPastryNodeFactory(new RandomNodeIdFactory(), simulator);
    pastryNodes = new Vector();
  }

  /**
   * Get pastryNodes.last() to bootstrap with, or return null.
   */
  protected NodeHandle getBootstrap() {
    NodeHandle bootstrap = null;

    try {
      PastryNode lastnode = (PastryNode) pastryNodes.lastElement();
      bootstrap = lastnode.getLocalHandle();
    } catch (NoSuchElementException e) {
    }

    return bootstrap;
  }

  /**
   * initializes the network and prepares for testing
   */
  protected void run() {
    for (int i=0; i<NUM_NODES; i++) {
      PastryNode node = factory.newNode(getBootstrap());
      if (i > 0)
        test(i, node.getLocalHandle());

      while (simulator.simulate()) {}

      System.out.println("CREATED NODE " + i + " " + node.getNodeId());

      pastryNodes.add(node);
    }
  }

  /**
   * starts the testing process
   */
  protected void test(int i, NodeHandle handle) {
    NodeId nodeId = handle.getNodeId();
    
    PastryNode bootNode = (PastryNode) pastryNodes.elementAt(random.nextInt(i));
    NodeHandle bootstrap = bootNode.getLocalHandle();
    
    NodeHandle closest = factory.getNearest(handle, bootstrap);
    NodeHandle realClosest = simulator.getClosest(nodeId);
    
    if (! closest.getNodeId().equals(realClosest.getNodeId())) {
      incorrect++;
      sum += (simulator.proximity(closest.getNodeId(), nodeId) / simulator.proximity(realClosest.getNodeId(), nodeId));
      
      System.out.println("ERROR: CLOSEST TO " + nodeId + " WAS " + closest.getNodeId() + " REAL CLOSEST: " + realClosest.getNodeId());
      System.out.println("SO FAR: " + incorrect + "/" + i + " PERCENTAGE: " + (sum/incorrect));
    }
  }

  /**
   * main
   */
  public static void main(String args[]) {
    ClosestRegrTest pt = new ClosestRegrTest();
    pt.run();
  }
}


