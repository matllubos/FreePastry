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

package rice.testharness.tests;

import rice.pastry.client.*;
import rice.pastry.leafset.*;
import rice.pastry.security.*;
import rice.pastry.messaging.*;
import rice.pastry.routing.*;
import rice.pastry.*;
import rice.pastry.direct.*;
import rice.pastry.wire.*;
import rice.pastry.standard.*;

import rice.scribe.*;
import rice.scribe.messaging.*;

import rice.testharness.*;
import rice.testharness.messaging.*;

import java.util.*;
import java.io.*;
import java.net.*;


/**
 * A test class which picks a number of random node IDs and
 * tests a pastry and direct ping to that NodeId.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */

public class LeafSetTest extends Test {

  private Credentials _credentials = new PermissiveCredentials();

  private TestHarness thl;

  private NodeId nodeId;

  /**
   * Constructor which takes the local node this test is on,
   * an array of all the nodes in the network, and a printwriter
   * to which to write data.
   *
   * @param out The PrintWriter to write test results to.
   * @param localNode The local Pastry node
   * @param nodes NodeHandles to all of the other participating
   *              TestHarness nodes (this test class ignores these)
   */
  public LeafSetTest(PrintStream out, PastryNode localNode) {
    super(out, localNode);

    nodeId = localNode.getNodeId();
  }

  /**
   * Method which is called when the TestHarness wants this
   * Test to begin testing.
   */
  public void startTest(final TestHarness thl, NodeId[] nodes) {
    LeafSet ls = _localNode.getLeafSet();
    TreeMap pastryNodesSorted = new TreeMap();
    boolean success = true;

    for (int i=0; i<nodes.length; i++) {
      pastryNodesSorted.put(nodes[i], nodes[i]);
    }

    // check size
    if ((ls.size() < ls.maxSize()) && ((pastryNodesSorted.size() - 1)*2 != ls.size())) {
      System.out.println("checkLeafSet: incorrect size " + nodeId +
                         " ls.size()=" + ls.size() + " total nodes=" +
                         pastryNodesSorted.size() + "\n" + ls);
      success = false;
    }

    // check for correct leafset range
    // ccw half
    for (int i=-ls.ccwSize(); i<0; i++) {
      NodeHandle nh = ls.get(i);

      if (! nh.isAlive()) {
        System.out.println("checkLeafSet: dead node handle " + nh.getNodeId() +
                           " in leafset at " + nodeId + "\n" + ls);
        success = false;
      }

      NodeId nid = ls.get(i).getNodeId();
      int inBetween;

      if (nodeId.compareTo(nid) > 0) // local > nid ?
        inBetween = pastryNodesSorted.subMap(nid, nodeId).size();
      else
        inBetween = pastryNodesSorted.tailMap(nid).size() +
                    pastryNodesSorted.headMap(nodeId).size();

      if (inBetween != -i) {
        System.out.println("checkLeafSet: failure at" + nodeId +
                           "i=" + i + " inBetween=" + inBetween + "\n" + ls);
        success = false;
      }
    }

    // cw half
    for (int i=1; i<=ls.cwSize(); i++) {
      NodeHandle nh = ls.get(i);

      if (! nh.isAlive()) {
        System.out.println("checkLeafSet: dead node handle " + nh.getNodeId() +
                           " in leafset at " + nodeId + "\n" + ls);
        success = false;
      }

      NodeId nid = ls.get(i).getNodeId();
      int inBetween;

      if (nodeId.compareTo(nid) < 0)   // localId < nid?
        inBetween = pastryNodesSorted.subMap(nodeId, nid).size();
      else
        inBetween = pastryNodesSorted.tailMap(nodeId).size() +
                    pastryNodesSorted.headMap(nid).size();

      if (inBetween != i) {
        System.out.println("checkLeafSet: failure at" + nodeId +
                           "i=" + i + " inBetween=" + inBetween + "\n" + ls);
        success = false;
      }
    }

    if (success)
      System.out.println("Leaf set test for node " + nodeId + " completed successfully.");
  }

  public Address getAddress() {
    return LeafSetTestAddress.instance();
  }

  public Credentials getCredentials() {
    return _credentials;
  }

  public void messageForAppl(Message msg) {
  }

  public static class LeafSetTestAddress implements Address {

    /**
    * The only instance of DumbTestAddress ever created.
    */
    private static LeafSetTestAddress _instance;

    /**
    * Returns the single instance of TestHarnessAddress.
    */
    public static LeafSetTestAddress instance() {
      if(null == _instance) {
        _instance = new LeafSetTestAddress();
      }
      return _instance;
    }

    /**
      * Code representing address.
      */
    public int _code = 0x969acc66;

    /**
      * Private constructor for singleton pattern.
      */
    private LeafSetTestAddress() {}

    /**
      * Returns the code representing the address.
      */
    public int hashCode() { return _code; }

    /**
      * Determines if another object is equal to this one.
      * Simply checks if it is an instance of AP3Address
      * since there is only one instance ever created.
      */
    public boolean equals(Object obj) {
      return (obj instanceof LeafSetTestAddress);
    }
  }  
}