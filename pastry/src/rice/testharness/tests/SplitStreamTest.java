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

import rice.testharness.*;
import rice.testharness.messaging.*;

import rice.pastry.*;
import rice.pastry.rmi.*;
import rice.pastry.standard.*;
import rice.pastry.join.*;
import rice.pastry.client.*;
import rice.pastry.messaging.*;
import rice.pastry.security.*;
import rice.pastry.routing.*;
import rice.pastry.leafset.*;
import rice.pastry.dist.*;

import rice.scribe.*;
import rice.scribe.maintenance.*;

import rice.splitstream.*;
import rice.splitstream.messaging.*;

import java.util.*;
import java.io.*;
import java.net.*;
import java.rmi.Naming;
import java.rmi.RMISecurityManager;
import java.rmi.RemoteException;
import java.security.*;

/**
* A test class which picks a number of random node IDs and
* tests a pastry and direct ping to that NodeId.
*
* @version $Id$
*
* @author Alan Mislove
*/

public class SplitStreamTest extends Test implements ISplitStreamApp, Observer {

  private Credentials credentials = new PermissiveCredentials();
  
  private Scribe scribe;

  private SplitStreamImpl splitStream;

  private NodeId CHANNEL_NODE_ID;

  private ChannelId CHANNEL_ID;

  private int base = 16;

  private String m_name = "SplitStreamTest";

  private Hashtable m_channels = new Hashtable();

  private int m_testFreq = Scribe.m_scribeMaintFreq;
  
  private int OUT_BW = 20;

  private int last_recv_time = 0;

  /**
   * Hashtable storing sequence numbers being published per stripe
   */
  public Hashtable m_stripe_seq = new Hashtable();

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
  public SplitStreamTest(PrintStream out, PastryNode localNode) {
    super(out, localNode);

    ChannelId channelId;

    scribe = new Scribe(localNode, credentials);
    scribe.setTreeRepairThreshold(3);

    CHANNEL_NODE_ID = scribe.generateTopicId(m_name);
    CHANNEL_ID = new ChannelId(CHANNEL_NODE_ID);
    splitStream = new SplitStreamImpl(localNode, scribe);
    splitStream.registerApp(this);
    
    _out.println("Created scribe/splitstram at " + localNode.getNodeId());
  }	

  /**
    * Method which is called when the TestHarness wants this
    * Test to begin testing.
    */
  public void startTest(final TestHarness thl, NodeId[] nodes) {
    if(getNodeId().equals(new NodeId(new byte[NodeId.nodeIdBitLength]))) {
      // creator of channel
      _out.println("Creating channel at " + getNodeId() + " with name " + m_name);
      createChannel(base, m_name);
    }
    else{
      _out.println("Attaching channel at " + getNodeId());
      attachChannel(CHANNEL_ID);
    }
  }
 
  public Address getAddress() {
    return SplitStreamTestAddress.instance();
  }

  public Credentials getCredentials() {
    return credentials;
  }

  public void messageForAppl(Message msg) {
    SplitStreamTestMessage tmsg = (SplitStreamTestMessage)msg;
    tmsg.handleDeliverMessage( this);
  }

  /**
    * Observer Implementation
   */
  public void update(Observable o, Object arg){
    byte[] data = (byte [])arg;
    Byte bt = new Byte(data[0]);
    StripeId stripeId = (StripeId)((Stripe)o).getStripeId();
    String str = stripeId.toString().substring(3,4);
    int recv_time = (int)System.currentTimeMillis();
    int diff;

    if(last_recv_time == 0)
      diff = 0;
    else
      diff = recv_time - last_recv_time;
    
    last_recv_time = recv_time;

    //_out.println("Application "+m_appIndex+" received data "+m_numRecv);
    /**
      * Logging style
     * <App_Index> <Host-name> <Stripe_num> <Seq_num> <Diff>
     */
    try{
      _out.println(getNodeId() + "\t"+InetAddress.getLocalHost().getHostName()+"\t"+str+"\t"+bt.intValue()+"\t"+diff);
    } catch(UnknownHostException e){
      _out.println(e);
    }

  }
  
  
  /**
    * This is a call back into the application
   * to notify it that one of the stripes was unable to
   * to find a parent, and thus unable to recieve data.
   */
  public void handleParentFailure(Stripe s){
  }  

  public void splitstreamIsReady(){
  }

  /**
    * Upcall that given channel is ready, i.e. it has all the
   * meta information for the channel, e.g. the number of
   * stripes, no. of spare capacity trees and their ids etc.
   */
  public void channelIsReady(ChannelId channelId) {
    Channel channel = (Channel)m_channels.get(CHANNEL_ID);
    _out.println("Channel " + CHANNEL_ID + " is ready, at " + getNodeId());
    // join all the stripes for the channel
    joinChannelStripes(CHANNEL_ID);


    // Trigger the periodic invokation of DistScribeRegrTest message
    _localNode.scheduleMsgAtFixedRate(makeSplitStreamTestMessage(credentials, CHANNEL_ID), 0 , m_testFreq*1000);
  }

  /**
    * Create a channel with given channelId
   *
   */
  public ChannelId createChannel(int numStripes, String name){
    Channel channel = splitStream.createChannel(base, name);
    m_channels.put(channel.getChannelId(), channel);
    channel.registerApp(this);
    channel.configureChannel(OUT_BW);
    if(channel.isReady())
      channelIsReady(channel.getChannelId());
    return channel.getChannelId();
  }

  public void attachChannel(ChannelId channelId){
    Channel channel = splitStream.attachChannel(channelId);
    m_channels.put(channel.getChannelId(), channel);
    channel.registerApp(this);
    channel.configureChannel(OUT_BW);
    if(channel.isReady())
      channelIsReady(channel.getChannelId());
    return;
  }

  public void showBandwidth(ChannelId channelId){
    Channel channel = (Channel) m_channels.get(channelId);
    BandwidthManager bandwidthManager = channel.getBandwidthManager();
    _out.println("Channel " + channelId + " has " +
                       bandwidthManager.getUsedBandwidth(channel) + " children ");
    return;
  }

  /**
    * Sends data on all the stripes of channel represented by channelId.
   */
  public void sendData(ChannelId channelId){
    Channel send = (Channel) m_channels.get(channelId);

    for(int i = 0; i < send.getNumStripes(); i++){
      StripeId stripeId = send.getStripes()[i];
      Stripe stripe = send.joinStripe(stripeId, this);
      OutputStream out = stripe.getOutputStream();
      _out.println("Sending on Stripe " + stripeId);
      //	    byte[] toSend = "Hello".getBytes() ;

      Integer seq = (Integer)m_stripe_seq.get((StripeId)stripeId);
      if(seq == null)
        seq = new Integer(0);
      byte[] toSend = new byte[1];
      toSend[0] = seq.byteValue();
      int seq_num = seq.intValue();
      seq = new Integer(seq_num + 1);
      m_stripe_seq.put(stripeId, seq);
      try{
        out.write(toSend, 0, 1 );
      }
      catch(IOException e){
        e.printStackTrace();
      }
    }
  }

  /**
    * Join all stripes associated with the given channeld
   */
  public void joinChannelStripes(ChannelId channelId){
    Channel channel =  (Channel) m_channels.get(channelId);

    // Join all the stripes associated with the channel
    while(channel.getNumSubscribedStripes() < channel.getNumStripes()){
      Stripe stripe = channel.joinAdditionalStripe(this);
    }
  }

  public boolean channelReady(ChannelId channelId){
    Channel channel =  (Channel) m_channels.get(channelId);
    return channel.isReady();
  }

  public boolean isRoot(NodeId topicId){
    return scribe.isRoot(topicId);
  }

  public Scribe getScribe(){
    return scribe;
  }


  public StripeId[] getStripeIds(ChannelId channelId){
    Channel channel = (Channel) m_channels.get(channelId);
    return channel.getStripes();
  }

  public int getNumStripes(ChannelId channelId){
    Channel channel = (Channel) m_channels.get(channelId);
    return channel.getNumStripes();
  }

  /**
    * Makes a DistSplitStreamTest message.
   *
   * @param c the credentials that will be associated with the message
   * @return the DistSplitStreamTestMessage
   */
  private Message makeSplitStreamTestMessage(Credentials c, ChannelId channelId) {
    return new SplitStreamTestMessage(getAddress(), c, channelId );
  }
  
  public static class SplitStreamTestAddress implements Address {

    /**
    * The only instance of SplitStreamTestAddress ever created.
     */
    private static SplitStreamTestAddress _instance;

    /**
    * Returns the single instance of TestHarnessAddress.
     */
    public static SplitStreamTestAddress instance() {
      if(null == _instance) {
        _instance = new SplitStreamTestAddress();
      }
      return _instance;
    }

    /**
      * Code representing address.
     */
    public int _code = 0x3474922a;

    /**
      * Private constructor for singleton pattern.
     */
    private SplitStreamTestAddress() {}

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
      return (obj instanceof SplitStreamTestAddress);
    }
  }		

  public static class SplitStreamTestMessage extends Message implements Serializable
  {

    private ChannelId m_channelId = null;
    /**
    * Constructor
     *
     * @param addr the address of the distSplitStreamTestApp receiver.
     * @param c the credentials associated with the mesasge.
     */
    public SplitStreamTestMessage( Address addr, Credentials c, ChannelId channelId) {
        super( addr, c );
        m_channelId = channelId;
      }

    /**
      * This method is called whenever the pastry node receives a message for the
     * DistSplitStreamTestApp.
     *
     * @param splitStreamApp the DistSplitStreamTestApp application.
     */
    public void handleDeliverMessage(SplitStreamTest test) {
      Channel channel = (Channel) test.m_channels.get(m_channelId);

      if (test.getNodeId().equals(new NodeId(new byte[NodeId.nodeIdBitLength]))){                                         // I am the creator of the channel
        test.sendData(channel.getChannelId());
      }
    }


    public String toString() {
      return new String( "SPLIT_STREAM_TEST  MSG:" );
    }
  }
}