

package rice.p2p.splitstream.testing;

import java.util.Random;

import rice.environment.Environment;
import rice.p2p.commonapi.*;
import rice.p2p.commonapi.testing.CommonAPITest;
import rice.p2p.splitstream.*;
import rice.pastry.PastrySeed;

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

    protected Random generateIdRng;

  /**
   * Constructor which sets up all local variables
   */
  public SplitStreamRegrTest(Environment env) {
    super(env);
    splitstreams = new SplitStreamImpl[NUM_NODES];
    ssclients = new SplitStreamTestClient[NUM_NODES];
    rng = new Random(PastrySeed.getSeed()+2);
    generateIdRng = new Random(PastrySeed.getSeed()+3);

  }


  /**
   * Usage: DistScribeRegrTest [-port p] [-bootstrap host[:port]] [-nodes n] [-protocol (rmi|wire)]
   * [-help]
   *
   * @param args DESCRIBE THE PARAMETER
   */
  public static void main(String args[]) {
      int seed;
      seed = (int)System.currentTimeMillis();
      //seed = 1202653027;
      PastrySeed.setSeed(seed);
      System.out.println("Seed= " + PastrySeed.getSeed());
      parseArgs(args);
    SplitStreamRegrTest splitstreamTest = new SplitStreamRegrTest(new Environment());
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
    testMaintenance(NUM_NODES/10);
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
	
	if(result && (total <= (NUM_NODES -1 ) * SplitStreamScribePolicy.DEFAULT_MAXIMUM_CHILDREN)){
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

    protected void testMaintenance(int num){
	sectionStart("Maintenance of multicast trees");
	stepStart("Killing Nodes");
	for(int i = 0; i < num; i++){
	    System.out.println("Killing "+ssclients[i].getId());
	    kill(i);
	    simulate();
	}
	if(checkTree(num, NUM_NODES))
	    stepDone(SUCCESS);
	else{
	    stepDone(FAILURE, "not all have parent");

	}
	stepStart("Tree Recovery");

	
	byte[] data = {0,1,0,1,1};
	boolean pass = true;
	for(int i = 0; i < 10; i++){
	    ssclients[rng.nextInt(NUM_NODES - num) + num].publishAll(data);
	    simulate();
	    
	    
	    int totalmsgs = 0;
	    for(int j = 0; j < NUM_NODES - num; j++){
		totalmsgs = totalmsgs + ssclients[j+num].getNumMesgs(); 
		if(ssclients[j+num].getNumMesgs() != 16)
		    System.out.println(ssclients[i+num].getId()+" recived "+ssclients[i+num].getNumMesgs());
		ssclients[j+num].reset();
	    }
	    //System.out.println("Expected " + ((NUM_NODES - num) * 16) + " messages, got " + totalmsgs);
	    if(totalmsgs != ((NUM_NODES -num)*16))
		pass = false;
	}

	if(pass){
	    stepDone(SUCCESS);
	}
	else{
	    stepDone(FAILURE);
	}
	
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
     if(checkTree(0, NUM_NODES))
	 stepDone(SUCCESS);
     else
	 stepDone(FAILURE,"not all stripes have a parent");
     stepStart("Sending Data");
     byte[] data = {0,1,0,1,1};
     ssclients[creator].publishAll(data);
     simulate();

     ssclients[creator].publishAll(new byte[0]);
     simulate();
     int totalmsgs = 0;
     for(int i = 0; i < NUM_NODES; i++){
        totalmsgs = totalmsgs + ssclients[i].getNumMesgs(); 
	ssclients[i].reset();
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

    protected boolean checkTree(int startindex, int num){
	Stripe[] stripes;
	boolean result = true;
	for(int i = startindex; i < num; i++){
	    stripes = ssclients[i].getStripes();
	    for(int j = 0; j < stripes.length; j++){
		if(stripes[j].getParent() == null && !stripes[j].isRoot()){
		    result = false;
		    System.out.println("Node "+ssclients[i].getId()+" is parent less for topic "+stripes[j].getStripeId().getId());
		}
		//if(stripes[j].getParent() == null && stripes[j].isRoot())
		//System.out.println("Node "+ssclients[i].getId()+" is parent less, but is the root for topic "+stripes[j].getStripeId().getId());
	    }
	}
	return result;
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
    generateIdRng.nextBytes(data);
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

      public void reset(){
	  numMesgsReceived = 0;
      }
      
      public Id getId(){
	  return channel.getLocalId();
      }
   private void log(String s){
      //System.out.println("" + n + " " + s);
   }

 }
}
