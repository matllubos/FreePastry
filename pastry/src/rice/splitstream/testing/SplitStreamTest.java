package rice.splitstream.testing;


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
import java.net.*;
import java.io.Serializable;
import java.security.*;


public class SplitStreamTest implements ISplitStreamApp, Observer{

  private EuclideanNetwork simulator; 
 private DirectPastryNodeFactory factory;
 private Vector pastrynodes;
 private Vector scribeNodes;
 private Vector splitStreamNodes;
 private Random rng;
 private RandomNodeIdFactory idFactory;
 private static int numNodes = 50;
 private static int port = 5009;
 private static String bshost;
 private static int bsport = 5009;
 private Credentials credentials = new PermissiveCredentials();
 
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
      SplitStreamTest test = new SplitStreamTest();
      test.init();
      test.createNodes();
      /** --CREATE -- **/
      Channel channel = test.createChannel(1);
      System.out.println(channel);
      /** -- ATTACH -- **/
      Channel channel2 = test.attachChannel(2, channel);
      while(test.simulate());
      /* Stripe stripeSender = channel.getSubscribedStripes()[0];
      StripeId striperecvId = channel2.getStripes()[0];
      Stripe striperecv = channel2.joinStripe(striperecvId, test);
      stripeSender.backdoorSend(new String("Hello")); */
      while(test.simulate());
      
   }

   public SplitStreamTest(){
     System.out.println("Creating a SplitStream");
   }
 
   public void init(){
      simulator = new EuclideanNetwork();
      idFactory = new RandomNodeIdFactory();
      factory = new DirectPastryNodeFactory(idFactory, simulator);
      scribeNodes = new Vector();    
      pastrynodes = new Vector();
      splitStreamNodes = new Vector();
      rng = new Random(5);
      
   }
   public Channel createChannel(int index){
	System.out.println("Attempting to create a Channel");
	Channel c = 
           ((ISplitStream) splitStreamNodes.elementAt(index)).createChannel(16);
	while(simulate());
	return c;
   }
   public Channel attachChannel(int index, Channel channel){
	System.out.println("Attempting to Attach to Channel");
	return(((ISplitStream) splitStreamNodes.elementAt(index)).attachChannel(channel.getChannelId()));
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
	System.out.println("Some data came");
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
    System.out.println("created " + pn);
  }

  /**
   * Creates the nodes used for testing.
   */
  protected void createNodes() {
    for (int i=0; i < numNodes; i++) {
      System.out.println("Making node: " + i);
      makeNode();
    }
    while(simulate());
    System.out.println("All Nodes Created Succesfully");
  }
  public boolean simulate() { 
	return simulator.simulate(); 
  }


}
