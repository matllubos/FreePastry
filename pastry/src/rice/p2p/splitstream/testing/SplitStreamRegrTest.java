package rice.p2p.splitstream.testing;

import java.io.Serializable;
import java.net.*;

import java.util.*;

import rice.*;

import rice.p2p.commonapi.*;
import rice.p2p.commonapi.testing.*;
import rice.p2p.splitstream.*;

/**
 * @(#) SplitStreamRegrTest.java Provides regression testing for the Scribe service using distributed
 * nodes.
 *
 * @version $Id$
 * @author Ansley Post 
 */

public class SplitStreamRegrTest extends CommonAPITest {

  // the instance name to use
  /**
   * DESCRIBE THE FIELD
   */
  public static String INSTANCE = "SplitStreamRegrTest";

  // the scribe impls in the ring
  /**
   * DESCRIBE THE FIELD
   */
  protected SplitStreamImpl splitstreams[];

  protected SplitStreamTestClient ssclients[];

  // a random number generator
  /**
   * DESCRIBE THE FIELD
   */
  protected Random rng;

  /**
   * Constructor which sets up all local variables
   */
  public SplitStreamRegrTest() {
    splitstreams = new SplitStreamImpl[NUM_NODES];
    ssclients = new SplitStreamTestClient[NUM_NODES];
    rng = new Random();
  }


  /**
   * Usage: DistScribeRegrTest [-port p] [-bootstrap host[:port]] [-nodes n] [-protocol (rmi|wire)]
   * [-help]
   *
   * @param args DESCRIBE THE PARAMETER
   */
  public static void main(String args[]) {
    parseArgs(args);
    SplitStreamRegrTest splitstreamTest = new SplitStreamRegrTest();
    splitstreamTest.start();
  }

  /**
   * Method which should process the given newly-created node
   *
   * @param node The newly created node
   * @param num The number of this node
   */
  protected void processNode(int num, Node node) {
    splitstreams[num] = new SplitStreamImpl(node, INSTANCE);
    ssclients[num] = new SplitStreamTestClient(node, splitstreams[num]);
  }

  /**
   * Method which should run the test - this is called once all of the nodes have been created and
   * are ready.
   */
  protected void runTest() {
    if (NUM_NODES < 2) {
      System.out.println("The DistScribeRegrTest must be run with at least 2 nodes for proper testing.  Use the '-nodes n' to specify the number of nodes.");
      return;
    }

    // Run each test
    testBasic();
    testBandwidthUsage();
    testIndependence();
  }

    protected void testBandwidthUsage(){
	boolean result = true;
	int count = 0;
	int total = 0;
	Channel channel;
	sectionStart("BandwidthUsage Test");
	stepStart("Usage");
	simulate();
	for(int i = 0; i < NUM_NODES; i++){
	    channel = ssclients[i].getChannel();
	    count = ((SplitStreamScribePolicy)splitstreams[i].getPolicy()).getTotalChildren(channel);
	    if(count > SplitStreamScribePolicy.DEFAULT_MAXIMUM_CHILDREN)
		result = false;
	    //System.out.println("count "+count);
	    total += count;
	}	
	//System.out.println("Total outgoing capacity Used "+total);
	
	if(result && (total == (NUM_NODES -1 ) * SplitStreamScribePolicy.DEFAULT_MAXIMUM_CHILDREN)){
	    stepDone(SUCCESS);
	}
	else{
	    stepDone(FAILURE);
	}
	sectionDone();
    }

    protected void testIndependence(){
	boolean result = true;
	int count = 0;
	int num = 0;
	int[] array = new int[20];
	Channel channel;
	Stripe[] stripes;
	sectionStart("Path Independence Test");
	stepStart("Usage");
	simulate();
	for(int i = 0; i < NUM_NODES; i++){
	    channel = ssclients[i].getChannel();
	    stripes = channel.getStripes();
	    num = 0;
	    for(int j = 0 ; j < stripes.length; j++){
		count = stripes[j].getChildren().length;
		if(count > 0)
		    num++;
	    }
	    array[num] ++;
	}
	for(int i = 0; i < 20; i++)
	    System.out.println(i+"\t"+array[i]);
	sectionDone();
    }

  /*
   *  ---------- Test methods and classes ----------
   */
  /**
   * Tests routing a Past request to a particular node.
   */
  protected void testBasic() {
     sectionStart("Basic Test");
     stepStart("Creating Channel");
     int creator  = rng.nextInt(NUM_NODES);
     ChannelId id = new ChannelId(generateId());
     ssclients[creator].createChannel(id);
     simulate();
     stepDone(SUCCESS);
     stepStart("Attaching and Joining Stripes");
     for(int i = 0; i < NUM_NODES; i++){
	 ssclients[i].attachChannel(id);
	 simulate();
     }
     for(int i = 0; i < NUM_NODES; i++){
	 ssclients[i].getStripes();
	 simulate();
     }
     for(int i = 0; i < NUM_NODES; i++){
	 ssclients[i].subscribeStripes();
	 simulate();
     }
     stepDone(SUCCESS);
     stepStart("Sending Data");
     byte[] data = {0,1,0,1,1};
     ssclients[creator].publishAll(data);
     simulate();

     ssclients[creator].publishAll(new byte[0]);
     simulate();
     int totalmsgs = 0;
     for(int i = 0; i < NUM_NODES; i++){
        totalmsgs = totalmsgs + ssclients[i].getNumMesgs(); 
     }

     if(totalmsgs == (NUM_NODES * 16 * 2)){
        stepDone(SUCCESS);
     }
     else{
        stepDone(FAILURE, "Expected " + (NUM_NODES * 16 * 2) + " messages, got " + totalmsgs);
     }
     sectionDone();
     testFailure(1);
  }
 
  protected void testFailure(int numnodes){
     sectionStart("Failure Test");
     sectionDone();
  }

  /**
   * Private method which generates a random Id
   *
   * @return A new random Id
   */
  private Id generateId() {
    byte[] data = new byte[20];
    new Random().nextBytes(data);
    return FACTORY.buildId(data);
  }
 
  private class SplitStreamTestClient implements SplitStreamClient{
  
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
   public SplitStreamTestClient(Node n, SplitStream ss){
      this.n = n;
      this.ss =ss;
      log("Client Created " + n);
   }

      public Channel getChannel(){
	  return this.channel;
      }
   public void joinFailed(Stripe s){
      log("Join Failed on " + s);
   }

   public void deliver(Stripe s, byte[] data){
      log("Data recieved on " + s);
      numMesgsReceived++;
  }
   
   public void createChannel(ChannelId cid){
      log("Channel " + cid + " created."); 
      channel = ss.createChannel(cid);
   }

   public void attachChannel(ChannelId cid){
      log("Attaching to Channel " + cid + "."); 
      if(channel == null)
        channel = ss.attachChannel(cid);
   }

   public Stripe[] getStripes(){
      log("Retrieving Stripes.");
      stripes = channel.getStripes();
      return stripes;
   }

   public void subscribeStripes(){
      log("Subscribing to all Stripes.");
      for(int i = 0; i < stripes.length ; i ++){
         stripes[i].subscribe(this);
      } 
   }
   public void publishAll(byte[] b){
     log("Publishing to all Stripes.");
     for(int i = 0; i < stripes.length; i++){
        publish(b, stripes[i]);
     }
   }
   public void publish(byte[] b, Stripe s){
     log("Publishing to " + s);
       s.publish(b);
   }

   public int getNumMesgs(){
     return numMesgsReceived;
   }

   private void log(String s){
      //System.out.println("" + n + " " + s);
   }

 }
}
