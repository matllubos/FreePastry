/*************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate 

Copyright 2002, Rice University. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither  the name  of Rice  University (RICE) nor  the names  of its
contributors may be  used to endorse or promote  products derived from
this software without specific prior written permission.

This software is provided by RICE and the contributors on an "as is"
basis, without any representations or warranties of any kind, express
or implied including, but not limited to, representations or
warranties of non-infringement, merchantability or fitness for a
particular purpose. In no event shall RICE or contributors be liable
for any direct, indirect, incidental, special, exemplary, or
consequential damages (including, but not limited to, procurement of
substitute goods or services; loss of use, data, or profits; or
business interruption) however caused and on any theory of liability,
whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even
if advised of the possibility of such damage.

********************************************************************************/

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

  public static int NUM_NODES = 10000;
  public static int NUM_TRIALS = 1000;

  private PastryNodeFactory factory;
  private NetworkSimulator simulator;
  private Vector pastryNodes;

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
  protected void initialize() {
    for (int i=0; i<NUM_NODES; i++) {
      PastryNode node = factory.newNode(getBootstrap());

      while (simulator.simulate()) {}

      System.out.println("CREATED NODE " + i + " " + node.getNodeId());

      pastryNodes.add(node);
    }
  }

  /**
   * starts the testing process
   */
  protected void start() {
    Random random = new Random();
    int incorrect = 0;
    double sum = 0;

    for (int i=0; i<NUM_TRIALS; i++) {
      PastryNode node = (PastryNode) pastryNodes.elementAt(random.nextInt(NUM_NODES));
      NodeId nodeId = node.getNodeId();
      NodeHandle handle = node.getLocalHandle();

      PastryNode bootNode = (PastryNode) pastryNodes.elementAt(random.nextInt(NUM_NODES));
      NodeHandle bootstrap = bootNode.getLocalHandle();

      NodeHandle closest = factory.getNearest(handle, bootstrap);
      NodeHandle realClosest = simulator.getClosest(nodeId);

      if (! closest.getNodeId().equals(realClosest.getNodeId())) {
        incorrect++;
        sum += (simulator.proximity(closest.getNodeId(), nodeId) / simulator.proximity(realClosest.getNodeId(), nodeId));

        System.out.println("SO FAR: " + incorrect + "/" + i + " PERCENTAGE: " + (sum/incorrect));
      }
    }
  }

  /**
   * main
   */
  public static void main(String args[]) {
    ClosestRegrTest pt = new ClosestRegrTest();
    pt.initialize();
    pt.start();
  }
}


