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
 * A test class which picks determines the ping values to all of the other
 * nodes in the test harness network
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */

public class PastryPinger extends Test {

  private Credentials _credentials = new PermissiveCredentials();

  private static int NUM_PINGS = 50;
  
  private int trial;
  
  private long pingStart;

  private long thisPingStart;

  private NodeHandle handle;
  
  private NodeId nodeId;

  private NodeHandle handles[];

  private long total[];

  private long min[];

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
  public PastryPinger(PrintStream out, PastryNode localNode) {
    super(out, localNode);

    nodeId = localNode.getNodeId();
    handle = localNode.getLocalHandle();
  }

  /**
   * Method which is called when the TestHarness wants this
   * Test to begin testing.
   */
  public void startTest(final TestHarness thl, NodeHandle[] nodes) {
    total = new long[nodes.length];
    min = new long[nodes.length];

    Arrays.fill(total, -1);
    Arrays.fill(min, Integer.MAX_VALUE);
    
    handles = nodes;
    trial = -1;

    for (int i=0; ((i<nodes.length) && (trial == -1)); i++) {
      if (nodes[i].getNodeId().equals(_localNode.getNodeId())) {
        trial = i;
      }
    }

    sendMessage(0);
  }

  private void sendMessage(int num) {
    if (num == 0)
      pingStart = System.currentTimeMillis();
    
    // create a PingMessage
    PastryPingerMessage pm = new PastryPingerMessage(trial, num, nodeId);

    thisPingStart = System.currentTimeMillis();
    
    // send message
    routeMsg(handles[trial].getNodeId(), pm, _credentials, null);
  }
    
  public Address getAddress() {
    return PastryPingerAddress.instance();
  }

  public Credentials getCredentials() {
    return _credentials;
  }

  public void messageForAppl(Message msg) {
    if (msg instanceof PastryPingerMessage) {
      // if were are here, we are the destination of a PingMessage.
      // we simply respond to the sender
      PastryPingerMessage pm = (PastryPingerMessage) msg;
      PastryPingerResponseMessage prm = pm.getResponse();
        
      routeMsg(pm.getSource(), prm, _credentials, null);
    } else if (msg instanceof PastryPingerResponseMessage) {
      // if we are here, we recieved a response from one of our pings
      // we measure the time difference from the timestamp on the message,
      // and record the result.
      //
      // we then send a direct ping to the responder to compare this time with
      PastryPingerResponseMessage prm = (PastryPingerResponseMessage) msg;


      if (prm.getNum() == trial) {
        if (prm.getTrial() < NUM_PINGS) {
          long thisTime = System.currentTimeMillis() - thisPingStart;

          if (thisTime < min[trial])
            min[trial] = thisTime;
          
          // create a PingMessage, and timestamp with the current time
          sendMessage(prm.getTrial() + 1);
        } else {
          long time = System.currentTimeMillis() - pingStart;

          total[trial] = time;

          _out.println("Pastry:\t" + 
                       ((WireNodeHandle) handle).getAddress().getAddress().getHostName() + " " + handle.getNodeId() + "\t" +
                       ((WireNodeHandle) handles[trial]).getAddress().getAddress().getHostName() + " " + handles[trial].getNodeId() + "\t" +
                       total[trial] + "\t" + min[trial]);

          trial = trial + DistTestHarnessRunner.NUM_NODES;
          
          
          if (trial > handles.length - 1) {
            trial = trial - handles.length;
          }

          if (total[trial] == -1) {
            sendMessage(0);
          } else {
            System.err.println("PastryPinger test completed at " + _localNode.getNodeId());
          }
        }
      }
    }
  }

  public static class PastryPingerMessage extends Message {

    private int num;
    
    private int trial;

    private NodeId node;

    public PastryPingerMessage(int num, int trial, NodeId node) {
      super(PastryPingerAddress.instance());
      this.num = num;
      this.trial = trial;
      this.node = node;
    }

    public PastryPingerResponseMessage getResponse() {
      return new PastryPingerResponseMessage(num, trial);
    }

    public NodeId getSource() {
      return node;
    }

    public String toString() {
      return "TimingPingMessage[from " + node + "]";
    }
  }

  public static class PastryPingerResponseMessage extends Message {

    private int num;
    
    private int trial;

    public PastryPingerResponseMessage(int num, int trial) {
      super(PastryPingerAddress.instance());
      this.num = num;
      this.trial = trial;
    }

    public int getTrial() {
      return trial;
    }

    public int getNum() {
      return num;
    }
    
    public String toString() {
      return "TimingPingResponseMessage";
    }
  }

  public static class PastryPingerAddress implements Address {

    /**
    * The only instance of PingerAddress ever created.
     */
    private static PastryPingerAddress _instance;

    /**
    * Returns the single instance of PingerAddress.
     */
    public static PastryPingerAddress instance() {
      if(null == _instance) {
        _instance = new PastryPingerAddress();
      }
      return _instance;
    }

    /**
      * Code representing address.
     */
    public int _code = 0x5586224a;

    /**
      * Private constructor for singleton pattern.
     */
    private PastryPingerAddress() {}

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
      return (obj instanceof PastryPingerAddress);
    }
  }
}