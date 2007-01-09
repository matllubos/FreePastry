/*******************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate

Copyright 2002-2007, Rice University. Copyright 2006-2007, Max Planck Institute 
for Software Systems.  All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither the name of Rice  University (RICE), Max Planck Institute for Software 
Systems (MPI-SWS) nor the names of its contributors may be used to endorse or 
promote products derived from this software without specific prior written 
permission.

This software is provided by RICE, MPI-SWS and the contributors on an "as is" 
basis, without any representations or warranties of any kind, express or implied 
including, but not limited to, representations or warranties of 
non-infringement, merchantability or fitness for a particular purpose. In no 
event shall RICE, MPI-SWS or contributors be liable for any direct, indirect, 
incidental, special, exemplary, or consequential damages (including, but not 
limited to, procurement of substitute goods or services; loss of use, data, or 
profits; or business interruption) however caused and on any theory of 
liability, whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even if 
advised of the possibility of such damage.

*******************************************************************************/ 
package rice.testharness.tests;

import rice.testharness.*;
import rice.testharness.messaging.*;

import rice.pastry.PastryNode;

import rice.p2p.scribe.*;
import rice.p2p.scribe.messaging.*;
import rice.p2p.commonapi.*;

import rice.p2p.splitstream.*;

import java.util.*;
import java.io.*;
import java.net.*;
import java.rmi.Naming;
import java.rmi.RMISecurityManager;
import java.rmi.RemoteException;
import java.security.*;

/**
 * A test class which picks a number of random node IDs and tests a pastry and
 * direct ping to that NodeId.
 * 
 * @version $Id$
 * 
 * @author Alan Mislove
 * @author Atul Singh
 */

public class SplitStreamTest extends Test {

  private CancellableTask sendDataTask = null;

  private SplitStream splitstream;

  private SplitStreamTestClient ssclient;

  private Id CHANNEL_NODE_ID;

  private ChannelId CHANNEL_ID;

  private int base = 16;

  // every 1000 ms, publish data on each stripe
  private int testFreq = /* 10* */1000;

  private int testDelay = /* 10* */10 * 1000;

  private int printTreePeriod = 10 * 60 * 1000;

  private int OUT_BW = 16;

  private long last_recv_time = 0;

  public int sequenceNum = 0;

  public static String INSTANCE = "DistSplitStreamTest";

  // 320KB/sec total => 16 KB/sec/stripe
  public int DATA_SIZE = 16;

  public byte[] content = new byte[DATA_SIZE * 1024 / base];

  /**
   * Constructor which takes the local node this test is on, an array of all the
   * nodes in the network, and a printwriter to which to write data.
   * 
   * @param out The PrintWriter to write test results to.
   * @param localNode The local Pastry node
   * @param nodes NodeHandles to all of the other participating TestHarness
   *          nodes (this test class ignores these)
   */
  public SplitStreamTest(PrintStream out, PastryNode localNode,
      TestHarness harness) {
    super(out, localNode, harness, "SPLITstreaM");
    splitstream = new SplitStreamImpl(localNode, INSTANCE);
    ssclient = new SplitStreamTestClient(localNode, splitstream);
    CHANNEL_ID = new ChannelId(generateId());
    ssclient.attachChannel(CHANNEL_ID);
    //System.setOut(out);
    //_out.println("Created scribe/splitstram at " + localNode.getNodeId()+",
    // created channel "+CHANNEL_ID);
  }

  /**
   * Method which is called when the TestHarness wants this Test to begin
   * testing.
   */
  public void startTest(NodeHandle[] nodes) {
    //System.out.println("SST.startTest()");
    ssclient.subscribeStripes();
    endpoint.scheduleMessage(new PrintTreeTestMessage(), printTreePeriod,
        printTreePeriod);

    // Trigger the periodic invokation of DistScribeRegrTest message
    // this is where the node has to be the zero node to run
    if (isNearestZero(ssclient.getId())) {
      createDataToPublish();
      // start publishing data after 60 seconds to allow nodes to join the trees
      sendDataTask = endpoint.scheduleMessage(makeSplitStreamTestMessage(),
          testDelay, testFreq);
      _out.println("DEBUG :: Scheduling message");
    }
  }

  private boolean isNearestZero(Id id) {
    return id.equals((Id) (rice.pastry.Id.build()));
  }

  /**
   * Private method which generates a random Id
   * 
   * @return A new random Id
   */
  private Id generateId() {
    byte[] data = new byte[20];
    //new Random(PastrySeed.getSeed()).nextBytes(data);
//    new Randon(100).nextBytes(data);
    splitstream.getEnvironment().getRandomSource().nextBytes(data);
    return rice.pastry.Id.build(data);
  }

  public void createDataToPublish() {
    // if DATA_SIZE = 256 KB, it means, on each stripe we push
    // 4KB
    for (int j = 0; j < DATA_SIZE * 1024 / base; j++) {
      content[j] = (new Byte("0")).byteValue();
    }
  }

  /**
   * This method is called on the application at the destination node for the
   * given id.
   * 
   * @param id The destination id of the message
   * @param message The message being sent
   */
  public void deliver(Id id, Message msg) {
    if (msg instanceof SplitStreamTestMessage) {
      SplitStreamTestMessage tmsg = (SplitStreamTestMessage) msg;
      tmsg.handleDeliverMessage(this);
      _out.println("DEBUG :: Received message " + tmsg);
    }
    if (msg instanceof PrintTreeTestMessage) {
      PrintTreeTestMessage tmsg = (PrintTreeTestMessage) msg;
      tmsg.handleDeliverMessage(this);
      _out.println("DEBUG :: Received message " + tmsg);
    }
  }

  public Channel getChannel() {
    return this.ssclient.getChannel();
  }

  public Id getId() {
    return ssclient.getId();
  }

  public void printTree() {
    //      scribe.getTopics(ssclient);
    //      ssclient.getChannel();
    Stripe[] stripe = ssclient.getStripes();
    for (int i = 0; i < stripe.length; i++) {
      NodeHandle parent = stripe[i].getParent();
      NodeHandle[] children = stripe[i].getChildren();
      System.out.println(stripe[i] + " parent: " + parent + " numChildren: "
          + children.length);
      for (int j = 0; j < children.length; j++) {
        System.out.println(stripe[i] + " child: " + children[j]);
      }
    }
  }

  /**
   * Sends data on all the stripes of channel represented by channelId.
   */
  int MAX_SEND_NUM = 360;

  public void sendData() {
    //      System.out.println("SST.sendData(): "+getId());
    //      Thread.dumpStack();
    if (sequenceNum > MAX_SEND_NUM) {
      if (sendDataTask != null)
        sendDataTask.cancel();
      return;
    }
    String str = (new Integer(sequenceNum)).toString();
    str += "\t" + (new Long(_localNode.getEnvironment().getTimeSource().currentTimeMillis()).toString());
    str += "\t";
    byte[] toSend = new byte[DATA_SIZE * 1024 / base];
    System.arraycopy(str.getBytes(), 0, toSend, 0, str.getBytes().length);
    System.arraycopy(content, 0, toSend, str.getBytes().length + 1,
        toSend.length - (str.getBytes().length + 1));
    sequenceNum++;
    ssclient.publishAll(toSend);
  }

  private Message makeSplitStreamTestMessage() {
    System.out.println("SST.makeSplitStreamTestMessage()");
    return new SplitStreamTestMessage();
  }

  public static class SplitStreamTestMessage implements Message {

    /**
     * This method is called whenever the pastry node receives a message for the
     * DistSplitStreamTestApp.
     * 
     * @param splitStreamApp the DistSplitStreamTestApp application.
     */
    public void handleDeliverMessage(SplitStreamTest test) {
      //System.out.println("SST.handleDeliverMessage()");
      // this is where we decide that I am the one to start the test
      //      if (isNearestZero(test.getId())){
      // I am the creator of the channel
      test.sendData();
      //      }
    }

    public String toString() {
      return new String("SPLIT_STREAM_TEST  MSG:");
    }

    public int getPriority() {
      return Message.LOW_PRIORITY;
    }
  }

  public static class PrintTreeTestMessage implements Message {

    /**
     * This method is called whenever the pastry node receives a message for the
     * DistSplitStreamTestApp.
     * 
     * @param splitStreamApp the DistSplitStreamTestApp application.
     */
    public void handleDeliverMessage(SplitStreamTest test) {
      test.printTree();
    }

    public String toString() {
      return new String("PRINT_TREE MSG:");
    }

    public int getPriority() {
      return Message.LOW_PRIORITY;
    }
  }

  private class SplitStreamTestClient implements SplitStreamClient {

    /**
     * The underlying common api node
     *  
     */
    private Node n = null;

    /**
     * The stripes for a channel
     *  
     */
    private Stripe[] stripes;

    /**
     * The channel to be used for this test
     *  
     */
    private Channel channel;

    /**
     * The SplitStream service for this node
     *  
     */
    private SplitStream ss;

    private int numMesgsReceived = 0;

    private SplitStreamScribePolicy policy = null;

    public SplitStreamTestClient(Node n, SplitStream ss) {
      this.n = n;
      this.ss = ss;
      log("Client Created " + n);
    }

    public Channel getChannel() {
      return this.channel;
    }

    public void joinFailed(Stripe s) {
      log("Join Failed on " + s);
    }

    public void deliver(Stripe s, byte[] data) {
      //log("Data recieved on " + s);
      numMesgsReceived++;

      //Byte bt = new Byte(data[0]);
      String ds = new String(data);
      StringTokenizer tk = new StringTokenizer(ds);
      String seqNumber = tk.nextToken();
      String sentTime = tk.nextToken();
      Id stripeId = (rice.pastry.Id) (s.getStripeId().getId());
      String str = stripeId.toString().substring(3, 4);
      long recv_time = _localNode.getEnvironment().getTimeSource().currentTimeMillis();
      int diff;
      char[] c = str.toString().toCharArray();
      int stripe_int = c[0] - '0';
      if (stripe_int > 9)
        stripe_int = 10 + c[0] - 'A';
      else
        stripe_int = c[0] - '0';

      if (last_recv_time == 0)
        diff = 0;
      else
        diff = (int) (recv_time - last_recv_time);

      last_recv_time = recv_time;

      //_out.println("Application "+m_appIndex+" received data "+m_numRecv);
      /**
       * Logging style <App_Index><Host-name><Stripe_num><Seq_num><Diff>
       */
      _out.println(stripe_int + "\t" + seqNumber + "\t" + "\t" + sentTime
          + "\t" + recv_time);
    }

    public void createChannel(ChannelId cid) {
      log("Channel " + cid + " created.");
      channel = ss.createChannel(cid);
      getStripes();
    }

    public void attachChannel(ChannelId cid) {
      log("Attaching to Channel " + cid + ".");
      if (channel == null)
        channel = ss.attachChannel(cid);
      getStripes();
    }

    public Stripe[] getStripes() {
      log("Retrieving Stripes.");
      stripes = channel.getStripes();
      return stripes;
    }

    public void subscribeStripes() {
      log("Subscribing to all Stripes.");
      for (int i = 0; i < stripes.length; i++) {
        stripes[i].subscribe(this);
      }
    }

    public void publishAll(byte[] b) {
      log("Publishing to all Stripes.");
      for (int i = 0; i < stripes.length; i++) {
        publish(b, stripes[i]);
      }
    }

    public void publish(byte[] b, Stripe s) {
      //log("Publishing to " + s);
      s.publish(b);
    }

    public int getNumMesgs() {
      return numMesgsReceived;
    }

    public void reset() {
      numMesgsReceived = 0;
    }

    public Id getId() {
      return (rice.pastry.Id) channel.getLocalId();
    }

    private void log(String s) {
      _out.println("DEBUG :: " + n + " " + s);
    }

  }

}

