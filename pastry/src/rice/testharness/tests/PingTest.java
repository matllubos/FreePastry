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

public class PingTest extends Test {

  private Credentials _credentials = new PermissiveCredentials();

  // create random factory to generate random node ids
  RandomNodeIdFactory nidf = new RandomNodeIdFactory();

  // random number generator for sleeping
  private Random _random;

  private static int NUM_PINGS = 100;
  private static int NUM_TRIALS = 100;
  
  private int trial;

  private TestHarness thl;

  private InetSocketAddress address;
  
  private WireNodeHandlePool pool;

  private NodeId nodeId;

  private long pastryPingStart;
  private long directPingStart;

  private long lastPingTime;

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
  public PingTest(PrintStream out, PastryNode localNode) {
    super(out, localNode);

    _random = new Random(System.currentTimeMillis());

    address = ((WireNodeHandle) _localNode.getLocalHandle()).getAddress();
    pool = (WireNodeHandlePool) ((WirePastryNode) _localNode).getNodeHandlePool();
    nodeId = _localNode.getNodeId();
    trial = 0;
  }

  /**
   * Method which is called when the TestHarness wants this
   * Test to begin testing.
   */
  public void startTest(final TestHarness thl, NodeId[] nodes) {
    trial++;
  
    // pick random NodeId
    NodeId nid = nidf.generateNodeId();

    // create a PingMessage
    TimingPingMessage pm = new TimingPingMessage(0, nodeId, address, false);
    pastryPingStart = System.currentTimeMillis();

    // send message
    routeMsg(nid, pm, _credentials, null);
  }

  public Address getAddress() {
    return PingTestAddress.instance();
  }

  public Credentials getCredentials() {
    return _credentials;
  }

  public void messageForAppl(Message msg) {
    if (msg instanceof TimingPingMessage) {
      // if were are here, we are the destination of a PingMessage.
      // we simply respond (directly) to the sender
      TimingPingMessage pm = (TimingPingMessage) msg;
      TimingPingResponseMessage prm = pm.getResponse(nodeId, address);

      if (pm.isDirect()) {
        NodeHandle nodeH = pool.get(pm.getNodeId());
        
        if (nodeH == null) {   
          nodeH = new WireNodeHandle(pm.getAddress(), pm.getNodeId(), _localNode);
        }
        
        routeMsgDirect(nodeH, prm, _credentials, null);
      } else {
        routeMsg(pm.getNodeId(), prm, _credentials, null);
      }
    } else if (msg instanceof TimingPingResponseMessage) {
      // if we are here, we recieved a response from one of our pings
      // we measure the time difference from the timestamp on the message,
      // and record the result.
      //
      // we then send a direct ping to the responder to compare this time with
      TimingPingResponseMessage prm = (TimingPingResponseMessage) msg;

      if (! prm.isDirect()) {
        if (prm.getTrial() < NUM_PINGS) {
          // create a PingMessage, and timestamp with the current time
          TimingPingMessage pm = new TimingPingMessage(prm.getTrial() + 1, nodeId, address, false); 
          routeMsg(prm.getNodeId(), pm, _credentials, null);
        } else {
          long pastryTime = (System.currentTimeMillis() - pastryPingStart);

          System.out.print(pastryTime + "  \t");
        
          NodeHandle nodeH = new WireNodeHandle(prm.getAddress(), prm.getNodeId(), _localNode);
          
          // create a PingMessage
          TimingPingMessage pm = new TimingPingMessage(0, nodeId, address, true);
          directPingStart = System.currentTimeMillis();

          // send message
          routeMsgDirect(nodeH, pm, _credentials, null);
        }
      } else {
        if (prm.getTrial() < NUM_PINGS) {
          NodeHandle nodeH = pool.get(prm.getNodeId());
        
          if (nodeH == null) {   
            nodeH = new WireNodeHandle(prm.getAddress(), prm.getNodeId(), _localNode);
          }
        
          // create a PingMessage, and timestamp with the current time
          TimingPingMessage pm = new TimingPingMessage(prm.getTrial() + 1, nodeId, address, true); 
          routeMsgDirect(nodeH, pm, _credentials, null);
        } else {
          long directTime = (System.currentTimeMillis() - directPingStart);

          System.out.println("" + directTime);
          
          if (trial < NUM_TRIALS) 
            startTest(null, null);
        }
      }
    }
  }

  public static class TimingPingMessage extends Message {

    private int trial;

    private long beginTime;

    private NodeId node;

    private String trace;

    private boolean isDirect;

    private InetSocketAddress address;

    public TimingPingMessage(int trial, NodeId node, InetSocketAddress address, boolean isDirect) {
      super(PingTestAddress.instance());
      this.trial = trial;
      this.node = node;
      this.address = address;
      this.isDirect = isDirect;

      this.beginTime = System.currentTimeMillis();
      this.trace = "";

      markTime("TimingPingMessage constructed");
    }

    public void markTime(String message) {
      long difference = System.currentTimeMillis() - beginTime;
      trace += difference + "\t " + message + "\n";
    }

    public TimingPingResponseMessage getResponse(NodeId nodeId, InetSocketAddress address) {
      return new TimingPingResponseMessage(trial, beginTime, nodeId, address, trace, isDirect);
    }

    public NodeId getNodeId() {
      return node;
    }

    public InetSocketAddress getAddress() {
      return address;
    }

    public boolean isDirect() {
      return isDirect;
    }

    public int getTrial() {
      return trial;
    }

    public String toString() {
      return "TimingPingMessage[from " + node + "]";
    }
  }

  public static class TimingPingResponseMessage extends Message {

    private int trial;

    private long beginTime;

    private NodeId node;

    private String trace;

    private InetSocketAddress address;

    private boolean isDirect;

    public TimingPingResponseMessage(int trial, long begintime, NodeId node, InetSocketAddress address, String trace, boolean isDirect) {
      super(PingTestAddress.instance());
      this.trial = trial;
      this.node = node;
      this.beginTime = begintime;
      this.address = address;
      this.trace = trace;
      this.isDirect = isDirect;
    }

    public void markTime(String message) {
      long difference = System.currentTimeMillis() - beginTime;
      trace += difference + "\t " + message + "\n";
    }

    public boolean isDirect() {
      return isDirect;
    }

    public NodeId getNodeId() {
      return node;
    }

    public int getTrial() {
      return trial;
    }

    public InetSocketAddress getAddress() {
      return address;
    }

    public String toString() {
      return "TimingPingResponseMessage[from " + node + "]\n" + trace;
    }
  }

  public static class IntermediateHop implements Serializable {

    private InetAddress _address;
    private NodeId _node;

    public IntermediateHop(NodeId node, InetAddress address) {
      _node = node;
      _address = address;
    }

    public NodeId getNodeId() {
      return _node;
    }

    public InetAddress getInetAddress() {
      return _address;
    }
  }
  
  public static class PingTestAddress implements Address {

    /**
    * The only instance of DumbTestAddress ever created.
     */
    private static PingTestAddress _instance;

    /**
    * Returns the single instance of TestHarnessAddress.
     */
    public static PingTestAddress instance() {
      if(null == _instance) {
        _instance = new PingTestAddress();
      }
      return _instance;
    }

    /**
      * Code representing address.
     */
    public int _code = 0x203cc561;

    /**
      * Private constructor for singleton pattern.
     */
    private PingTestAddress() {}

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
      return (obj instanceof PingTestAddress);
    }
  }
}