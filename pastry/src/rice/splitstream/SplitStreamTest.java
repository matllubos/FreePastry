package rice.splitstream;


import rice.*;

import rice.past.*;
import rice.past.messaging.*;

import rice.pastry.*;
import rice.pastry.dist.*;
import rice.pastry.direct.*;
import rice.pastry.standard.*;
import rice.pastry.security.*;
import rice.pastry.routing.*;
import rice.post.*;
import rice.post.messaging.*;
import rice.post.storage.*;

import rice.scribe.*;
import rice.scribe.testing.*;
import rice.storage.*;

import java.util.*;
import java.io.*;
import java.net.*;
import java.io.Serializable;
import java.security.*;


public class SplitStreamTest implements ISplitStreamApp, Observer{

  private EuclideanNetwork simulator; 
 private DirectPastryNodeFactory factory;
 private Vector pastrynodes;
 private Vector scribeNodes;
 private Vector splitStreamNodes;
 private Vector channels;
 private Random rng;
 private RandomNodeIdFactory idFactory;
 private static int numNodes = 500;
 private static int port = 5009;
 private static String bshost;
 private static int bsport = 5009;
 private Credentials credentials = new PermissiveCredentials();
 private int numResponses = 0;
 
 private static int protocol = DistPastryNodeFactory.PROTOCOL_WIRE;

  static {
    try {
      bshost = InetAddress.getLocalHost().getHostName();
    } catch (UnknownHostException e) {
      System.out.println("Error determining local host: " + e);
    }
  }


   public static void main(String argv[]){
      System.out.println("SplitStream Test Program v0.4");
      PastrySeed.setSeed((int)System.currentTimeMillis());
      //PastrySeed.setSeed( -1022990516 );
      System.out.println(PastrySeed.getSeed() );
      SplitStreamTest test = new SplitStreamTest();
      test.init();
      test.createNodes();
      /** --CREATE -- **/
      Channel content = test.createChannel();
      ChannelId channelId = content.getChannelId();
      System.out.println(content);
      while(test.simulate());
      /** -- ATTACH -- **/
      for(int i = 0; i < test.splitStreamNodes.size(); i ++){
	Channel channel =  test.attachChannel(i, channelId);
       test.channels.add(channel);
       
       while(test.simulate());
       if(channel.getSpareCapacityId() == null){
        System.out.println("Channel " + channel.getNodeId() + "Failed to Attach");      System.out.println("Index = " + i);
        System.exit(0);
       }
       else{
	//System.out.println("Channel Attached Succesfully " + channel.getNodeId());
   } 
       
      }
      while(test.simulate());
      System.out.println("All nodes attached to Channel");
      System.out.println("All nodes are joining all stripes");
      test.join();
      while(test.simulate());
      test.send(content);
      while(test.simulate());
      test.send(content);
      while(test.simulate());
      test.showBandwidth();

      System.out.println(PastrySeed.getSeed() );
   }

   public SplitStreamTest(){
     System.out.println("Creating a SplitStream");
   }
 
   public void init(){
      simulator = new EuclideanNetwork();
      idFactory = new RandomNodeIdFactory();
      //PastrySeed.setSeed((int)System.currentTimeMillis());
      factory = new DirectPastryNodeFactory(idFactory, simulator);
      scribeNodes = new Vector();    
      pastrynodes = new Vector();
      channels = new Vector();
      splitStreamNodes = new Vector();
      rng = new Random(5);
      
   }
   public Channel createChannel(){
	System.out.println("Attempting to create a Channel");
        int base = RoutingTable.baseBitLength();
	Channel c = 
           ((ISplitStream) splitStreamNodes.elementAt(rng.nextInt(numNodes))).createChannel(1<<base,"SplitStreamTest");
	while(simulate());
	return c;
   }
   public Channel attachChannel(int index, ChannelId channelId){
	return(((ISplitStream) splitStreamNodes.elementAt(index)).attachChannel(channelId));
   }
   public void join(){
	for(int i = 0; i < channels.size(); i ++){
	  Channel channel = (Channel) channels.elementAt(i);
	  while(channel.getNumSubscribedStripes() < channel.getNumStripes()){
                Stripe stripe = channel.joinAdditionalStripe(this);
	  }
	
	}
   }
   public void send(Channel send){
	StripeId stripeId = send.getStripes()[rng.nextInt(send.getNumStripes())];
	Stripe stripe = send.joinStripe(stripeId, this);
	OutputStream out = stripe.getOutputStream();
	System.out.println("Sending on Stripe " + stripe.getStripeId());
	byte[] toSend = "Hello".getBytes() ;
	try{
	   out.write(toSend, 0, toSend.length );
	}
	catch(IOException e){
	   e.printStackTrace();
	}
        numResponses = 0;
   }
   public void showBandwidth(){

	for(int i = 0; i < channels.size(); i ++){
	  Channel channel = (Channel) channels.elementAt(i);
	  BandwidthManager bandwidthManager = channel.getBandwidthManager();
          System.out.println("Channel " + channel.getNodeId() + " has " +
               bandwidthManager.getUsedBandwidth(channel) + " children "); 
	  }
   }
   /**
    * ISplitStreamApp Implementation
    */
   public void handleParentFailure(Stripe s){
      /* Do some error recovery in real app */
      /* Here just print message and exit */
      System.out.println("Unrecoverable tree failure. Exiting ...");
      System.exit(0);
   }
   /**
    * Observer Implementation
    */
   public void update(Observable o, Object arg){
	numResponses++;
   	if(numResponses == numNodes){
		System.out.println("All Stripes Have Recieved Message on Stripe" + ((Stripe) o).getStripeId());
 	}
   }

  /* ---------- Setup methods ---------- */

  /**
   * Gets a handle to a bootstrap node.
   *
   * @return handle to bootstrap node, or null.
   */
  //protected NodeHandle getBootstrap() {
   // InetSocketAddress address = new InetSocketAddress(bshost, bsport);
    //return factory.getNodeHandle(address);
  //}
 
   private NodeHandle getBootstrap() {
	NodeHandle bootstrap = null;
	try {
	    PastryNode lastnode = (PastryNode) pastrynodes.lastElement();
	    bootstrap = lastnode.getLocalHandle();
	} catch (NoSuchElementException e) {
	}
	return bootstrap;
    }

  /**
   * Creates a pastryNode with a past, scribe, and post running on it.
   */
  protected void makeNode() {
    PastryNode pn = factory.newNode(getBootstrap());
    pastrynodes.add(pn);
    Scribe scribe = new Scribe(pn, credentials);
    scribeNodes.add(scribe);  
    ISplitStream ss = new SplitStreamImpl(pn, scribe);
    splitStreamNodes.add(ss);
    //System.out.println("created " + pn);
  }

  /**
   * Creates the nodes used for testing.
   */
  protected void createNodes() {
    for (int i=0; i < numNodes; i++) {
      makeNode();
      System.out.print("<"+i+">");
      while(simulate());
    }
    while(simulate());
    System.out.println("All Nodes Created Succesfully");
  }
  public boolean simulate() { 
	return simulator.simulate(); 
  }

    public void channelIsReady(ChannelId channelId){
    }

    public void splitstreamIsReady(){
    }

}
