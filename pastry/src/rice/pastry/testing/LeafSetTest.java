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

import java.util.*;

import rice.pastry.*;
import rice.pastry.leafset.*;
import rice.pastry.messaging.*;
import rice.pastry.standard.*;

/**
 * This class tests the correctness of the leafset in Pastry.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */
public class LeafSetTest {

  protected NodeIdFactory factory;

  protected Random random;

  public LeafSetTest() {
    factory = new RandomNodeIdFactory();
    random = new Random();
  }

  public void start() {
    testCumulativeRange();
    testNonCumulativeRange();
  }

  /**
   * Throws an exception if the test condition is not met.
   */
  protected final void assertTrue(String intention, boolean test) {
    if (!test) {
      System.out.println(intention + " - failed.");
      System.exit(0);
    }
  }

  protected int min(int x, int y) {
    if (y<x) return y;
    else return x;
  }

  protected int abs(int x) {
    if (x<0) return -x;
    else return x;
  }

  public void testCumulativeRange() {
    for (int nodes=2; nodes<20; nodes++) {
      for (int size=2; size<17; size+=2) {
        LeafSet leafset = generateLeafSet(size, nodes, false);

        System.out.println("Testing cumulative ranges with " + nodes + " nodes and leafset size of " + size);

        for (int pos=-min(size/2,nodes/2); pos<=min(size/2,nodes/2); pos++) {
          
          for (int q=0; q<size; q++) {
            IdRange range = leafset.range(leafset.get(pos), q);
            if ((q < size/2 - abs(pos)) || (size + 1 > nodes)) {
              assertTrue("Range of node " + pos + " with q " + q + " nodes " + nodes + " size " + size +
                         " should be defined in leafset " + leafset, range != null);

              if (q >= nodes-1) {
                assertTrue("Range of node " + pos + " with q " + q + " nodes " + nodes + " size " + size +
                           " should be full in leafset " + leafset, range.isFull());
              } else {
                assertTrue("Range of node " + pos + " with q " + q + " nodes " + nodes + " size " + size +
                           " should be full in leafset " + leafset, ! range.isFull());
              }
            } else {
              assertTrue("Range of node " + pos + " with q " + q + " nodes " + nodes + " size " + size +
                         " should be not defined in leafset " + leafset, range == null);
            }
          }
        }
      }
    }
  }

  public void testNonCumulativeRange() {
    for (int nodes=1; nodes<20; nodes++) {
      for (int size=2; size<17; size+=2) {
        LeafSet leafset = generateLeafSet(size, nodes, false);

        System.out.println("Testing non-cumulative ranges with " + nodes + " nodes and leafset size of " + size);

        for (int pos=-min(size/2,nodes/2); pos<=min(size/2,nodes/2); pos++) {

          for (int q=0; q<size; q++) {
            IdRange range = leafset.range(leafset.get(pos), q, true);

            if ((q < size/2 - abs(pos)) || (size + 1 > nodes)) {
              assertTrue("Range of node " + pos + " with q " + q + " nodes " + nodes + " size " + size +
                         " should not be null in leafset " + leafset, range != null);
              
              if (q >= nodes) {
                assertTrue("Range of node " + pos + " with q " + q + " nodes " + nodes + " size " + size +
                           " should be empty in leafset " + leafset, range.isEmpty());
              } else {
                assertTrue("Range of node " + pos + " with q " + q + " nodes " + nodes + " size " + size +
                           " should be defined in leafset " + leafset, ! range.isEmpty());
              }
            } else {
              assertTrue("Range of node " + pos + " with q " + q + " nodes " + nodes + " size " + size +
                         " should be not defined in leafset " + leafset, range == null);
            }
          }
        }

        if (size + 1 > nodes) {
          IdRange total = new IdRange();

          for (int q=0; q<size; q++) {
            total = total.merge(leafset.range(leafset.get(0), q, true));
            total = total.merge(leafset.range(leafset.get(0), q, false));
          }

          assertTrue("Sum of individual ranges should produce entire range with nodes " + nodes + " size " + size +
                     " in leafset " + leafset + " with total " + total, total.isFull());
        }
      }
    }
  }

  /**
   * Returns a leafset of size size out of a network with
   * the specified number of nodes
   *
   * @param size The size of the leaf set
   * @param nodes The number of nodes in the network
   * @param crossover Whether the leafset must cross over the '0' boundary not
   */
  protected LeafSet generateLeafSet(int size, int nodes, boolean crossover) {
    NodeHandle[] handles = new NodeHandle[nodes];

    for (int i=0; i<nodes; i++) {
      handles[i] = new TestNodeHandle(factory.generateNodeId());
    }

    Arrays.sort(handles);

    int i=0;
    
    if (crossover) {
      i = (nodes - size + random.nextInt(size)) % nodes;
    } else {
      i = random.nextInt(nodes);
    }

    int base = (i + size/2) % nodes;
    LeafSet set = new LeafSet(handles[base], size);

    for (int j=0; j<nodes; j++) {
      set.put(handles[j]);
    }

    return set;
  }

  public static void main(String args[]) {
    LeafSetTest test = new LeafSetTest();
    test.start();
  }

  public static class TestNodeHandle extends NodeHandle implements Comparable {
    private NodeId id;

    public TestNodeHandle(NodeId id) {
      this.id = id;
    }

    public NodeId getNodeId() { return id; }

    public boolean isAlive() { return true; }

    public int proximity() { return 1; }

    public boolean ping() { return true; }

    public boolean equals(Object obj) {
      if (obj instanceof TestNodeHandle) {
        return ((TestNodeHandle) obj).id.equals(id);
      }

      return false;
    }

    public int hashCode() { return id.hashCode(); }

    public void receiveMessage(Message m) {};

    public int compareTo(Object o) {
      return id.compareTo(((TestNodeHandle) o).id);
    }

    public String toString() { return id.toString(); }
  }
}


