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
import rice.pastry.dist.*;
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

public class TraceTest extends Test {

  // create random factory to generate random node ids
  RandomNodeIdFactory nidf = new RandomNodeIdFactory();

  private Credentials _credentials = new PermissiveCredentials();

  // counter to determine when we're done
  private int _total;

  // random number generator for sleeping
  private Random _random;

  private static int NUM_NODES_TO_PING = 50;

  private TestHarness thl;

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
  public TraceTest(PrintStream out, PastryNode localNode) {
    super(out, localNode);
    _total = NUM_NODES_TO_PING;
    _random = new Random(System.currentTimeMillis());
  }

  /**
   * Method which is called when the TestHarness wants this
   * Test to begin testing.
   */
  public void startTest(TestHarness thl, NodeHandle[] nodes) {
    NodeId nid = nidf.generateNodeId();

    // create a PingMessage, and timestamp with the current time
    TracePingMessage pm = new TracePingMessage(0, _localNode.getNodeId());

    // send the message
    routeMsg(nid, pm, _credentials, null);
  }

  public Address getAddress() {
    return TraceTestAddress.instance();
  }

  public Credentials getCredentials() {
    return _credentials;
  }

  public void messageForAppl(Message msg) {
    if (msg instanceof TracePingMessage) {

      // if were are here, we are the destination of a PingMessage.
      // we simply respond (directly) to the sender
      TracePingMessage pm = (TracePingMessage) msg;
      TracePingResponseMessage prm = pm.getResponse(_localNode.getNodeId(), ((WireNodeHandle) _localNode.getLocalHandle()).getAddress());

      routeMsg(pm.getNodeId(), prm, _credentials, null);
    } else if (msg instanceof TracePingResponseMessage) {
      TracePingResponseMessage prm = (TracePingResponseMessage) msg;

      System.err.println(prm.toString());

      if (prm.getTrial() < NUM_NODES_TO_PING) {
        NodeId nid = nidf.generateNodeId();

        // create a PingMessage, and timestamp with the current time
        TracePingMessage pm = new TracePingMessage(prm.getTrial() + 1, _localNode.getNodeId());

        // send the message
        routeMsg(nid, pm, _credentials, null);
      }
    }
  }


  public boolean enrouteMessage(Message msg, NodeId key, NodeId nextHop, SendOptions opt) {
    boolean result = super.enrouteMessage(msg, key, nextHop, opt);

    if (msg instanceof TracePingMessage) {
      ((TracePingMessage) msg).addIntermediateHop(_localNode.getNodeId());
    }

    return result;
  }

  public static class TracePingMessage extends Message {

    private int _i;

    private NodeId _node;

    private InetAddress _lastAddress;

    private int _numWANHops;
    private int _numTotalHops;

    private String _trace;

    public TracePingMessage(int i, NodeId node) {
      super(TraceTestAddress.instance());
      _i = i;
      _node = node;
      _numWANHops = 0;
      _numTotalHops = -2;

      setInetAddress();

      _trace = "";
    }

    public TracePingResponseMessage getResponse(NodeId nodeId, InetSocketAddress address) {
      return new TracePingResponseMessage(_i, nodeId, address, _numWANHops, _numTotalHops, _trace);
    }

    public NodeId getNodeId() {
      return _node;
    }

    public int getTrial() {
      return _i;
    }

    public void addIntermediateHop(NodeId node) {
      setInetAddress();

      if (_trace.equals("")) {
        _trace = _lastAddress.getHostAddress() + " (" + node + ")";
      } else {
        _trace += " -> " + _lastAddress.getHostAddress() + " (" + node + ")";
      }
    }

    private void setInetAddress() {
      try {
        if (_lastAddress == null) {
          _lastAddress = InetAddress.getLocalHost();
        } else {
          InetAddress newAddress = InetAddress.getLocalHost();

          if (! newAddress.equals(_lastAddress))
            _numWANHops++;

          _lastAddress = newAddress;
        }
      } catch (UnknownHostException e) {
      }

      _numTotalHops++;
    }

    public String toString() {
      return "TracePingMessage[from " + _node + "]";
    }
  }

  public static class TracePingResponseMessage extends Message {

    private int _i;

    private NodeId _node;

    private InetSocketAddress _address;

    private int _numWANHops;

    private int _numTotalHops;

    private String _trace;

    public TracePingResponseMessage(int i, NodeId node, InetSocketAddress address, int numWANHops, int numTotalHops,String trace) {
      super(TraceTestAddress.instance());
      _i = i;
      _node = node;
      _address = address;
      _numWANHops = numWANHops;
      _numTotalHops = numTotalHops;
      _trace = trace;
    }

    public int getTrial() {
      return _i;
    }

    public NodeId getNodeId() {
      return _node;
    }

    public InetSocketAddress getAddress() {
      return _address;
    }

    public int getNumWANHops() {
      return _numWANHops;
    }

    public int getNumTotalHops() {
      return _numTotalHops;
    }

    public String getTrace() {
      return _trace;
    }

    public String toString() {
      return "" + _numWANHops + "\t" + _numTotalHops + "\t" + _trace;
    }
  }  

  public static class TraceTestAddress implements Address {

    /**
    * The only instance of TraceTestAddress ever created.
    */
    private static TraceTestAddress _instance;

    /**
    * Returns the single instance of TestHarnessAddress.
    */
    public static TraceTestAddress instance() {
      if(null == _instance) {
        _instance = new TraceTestAddress();
      }
      return _instance;
    }

    /**
      * Code representing address.
      */
    public int _code = 0x5563cd32;

    /**
      * Private constructor for singleton pattern.
      */
    private TraceTestAddress() {}

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
      return (obj instanceof TraceTestAddress);
    }
  }  
}