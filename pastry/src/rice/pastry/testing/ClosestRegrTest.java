
package rice.pastry.testing;

import rice.environment.Environment;
import rice.pastry.*;
import rice.pastry.direct.*;
import rice.pastry.standard.*;

import java.io.*;
import java.util.*;

/**
 * ClosestRegrTest
 *
 * A test suite for the getClosest algorithm.  getClosest attempts to choose 
 * routing table entries with the closet proximity.
 * 
 * Consider this test a PASS if the closest node is there more than 50% of the 
 * time.  Potentially this test should be run daily and the proximity recorded
 * over time to see if there was a drastic change based on algorithmic change.
 *
 * @version $Id$
 *
 * @author alan mislove
 */
public class ClosestRegrTest {

  public static int NUM_NODES = 1000;

  private PastryNodeFactory factory;
  private NetworkSimulator simulator;
  private Vector pastryNodes;
  
  int incorrect = 0;
  double sum = 0;

  private Environment environment;
  /**
   * constructor
   */
  private ClosestRegrTest() throws IOException {
    environment = Environment.directEnvironment();
    simulator = new SphereNetwork(environment);
    factory = new DirectPastryNodeFactory(new RandomNodeIdFactory(environment), simulator, environment);
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
      synchronized(node) {
        while(!node.isReady()) {
          try {
            node.wait(500);          
          } catch (InterruptedException ie) {
            return; 
          }
        }
      }
      if (i > 0)
        test(i, (DirectNodeHandle)node.getLocalHandle());

//      while (simulator.simulate()) {}

      System.out.println("CREATED NODE " + i + " " + node.getNodeId());

      pastryNodes.add(node);
      double ave = getAvgNumEntries(pastryNodes);
      System.out.println("Avg Num Entries:"+ave);
    }
    
    System.out.println("SO FAR: " + incorrect + "/" + NUM_NODES + " PERCENTAGE: " + (sum/incorrect));
  }

  protected double getAvgNumEntries(Collection nds) {
    double sum = 0;
    Iterator i = nds.iterator(); 
    while(i.hasNext()) {
      PastryNode pn = (PastryNode)i.next(); 
      sum+=pn.getRoutingTable().numUniqueEntries();
    }
    return sum/nds.size();
  }
  
  /**
   * starts the testing process
   */
  protected void test(int i, DirectNodeHandle handle) {
    PastryNode bootNode = (PastryNode) pastryNodes.elementAt(environment.getRandomSource().nextInt(i));
    NodeHandle bootstrap = bootNode.getLocalHandle();
//    System.out.println();
    DirectNodeHandle closest = (DirectNodeHandle)factory.getNearest(handle, bootstrap);
    DirectNodeHandle realClosest = simulator.getClosest(handle);
        
    if (! closest.getNodeId().equals(realClosest.getNodeId())) {
      incorrect++;
      int cProx = simulator.proximity(closest, handle);
      int rProx = simulator.proximity(realClosest, handle);
      sum += (cProx / rProx);
      
      System.out.println("ERROR: CLOSEST TO " + handle + " WAS " + closest.getNodeId()+":"+ cProx + " REAL CLOSEST: " + realClosest.getNodeId()+":"+rProx);
      System.out.println("SO FAR: " + incorrect + "/" + i + " PERCENTAGE: " + (sum/incorrect));

//      NodeHandle closest2 = factory.getNearest(handle, bootstrap);
//      System.out.println(closest2);
//      NodeHandle realClosest2 = simulator.getClosest(nodeId);
    }
  }

  public boolean pass() {
    return incorrect < NUM_NODES/2;
  }
  
  /**
   * main
   */
  public static void main(String args[]) throws IOException {
    ClosestRegrTest pt = new ClosestRegrTest();
    pt.run();
    System.out.println("pass:"+pt.pass());
    pt.environment.destroy();
  }
}


