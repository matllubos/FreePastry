package rice.splitstream.testing;

import rice.*;

import rice.pastry.*;
import rice.pastry.direct.*;
import rice.pastry.security.*;
import rice.pastry.routing.*;
import rice.pastry.standard.*;

import rice.scribe.*;

import rice.splitstream.*;
import rice.splitstream.messaging.*;

import java.util.*;
/**
 * This test determines wheter the Channel is functioning as it is
 * supposed to be.
 *
 * @author Ansley Post
 *
 * @deprecated This version of SplitStream has been deprecated - please use the version
 *   located in the rice.p2p.splitstream package.
 */
public class ChannelUnitTest implements Observer{

 private EuclideanNetwork simulator;
 private DirectPastryNodeFactory factory;
 private Vector splitStreamNodes;
 private Credentials credentials = new PermissiveCredentials();
 private Vector pastrynodes;
 private static int numNodes = 50;
 private Random rng;
 private RandomNodeIdFactory idFactory;
 private Channel channel;
 private Scribe scribe;

 public static void main(String argv[]){
      ChannelUnitTest test = new ChannelUnitTest();
      test.run();
 } 

  public boolean run(){
      init();
      createNodes();
      setChannel(createChannel());
      return(testChannel());
  }
  public boolean testChannel(){
    boolean passed = true;
    System.out.println("");

    /**
     * Tests to see if there is a bandwidth associated with this channel
     * Succeeds: if non-null BandwidthManager is returned
     */
    if(getChannel().getBandwidthManager() != null){
      System.out.println("Get BandwidthManager        [ PASSED ]" );
    }
    else{
      System.out.println("Get BandwidthManager        [ FAILED ]" );
      passed = false;
    }

    /**
     * Tests to see if ChannelId is correctly returned 
     * Succeeds: if ChannelId is equal to the value of the generateTopicId
     * for the string the channel is created with. 
     */
    if(scribe.generateTopicId("ChannelUnitTest").equals(getChannel().getChannelId())){
      System.out.println("Get Channel Id              [ PASSED ]" );
    }
    else{
      System.out.println("Get Channel Id              [ FAILED ]" );
      passed = false;
    }

    /**
     * Tests to see if getStripes returns the correct number of stripes 
     * Succeeds: if the number of stripes equals numStripes 
     */
    if(getChannel().getStripes().length == getChannel().getNumStripes()){
      System.out.println("Get Stripes                 [ PASSED ]" );
    }
    else{
      System.out.println("Get Stripes                 [ FAILED ]" );
      passed = false;
    }

    /**
     * Tests to see if getSubscribedStripes returns 0 before a stripe
     * is subscribed to.
     * Succeeds: if the number of  subscribed stripes equals 0 
     */
    if(getChannel().getSubscribedStripes().size() == 0){
      System.out.println("Get Subscribed Stripes(none)[ PASSED ]" );
    }
    else{
      System.out.println("Get Subscribed Stripes(none)[ FAILED ]" );
      passed = false;
    } 

    /**
     * Tests to see if getSubscribedStripes becomes 1 when join called 
     * Succeeds: if the number of  subscribed stripes equals  1
     */
    getChannel().joinAdditionalStripe(this);
    if(getChannel().getSubscribedStripes().size() == 1){
      System.out.println("Join Additional Stripe(one) [ PASSED ]" );
    }
    else{
      System.out.println("Join Additional Stripe(one) [ FAILED ]" );
      passed = false;
    } 

    /**
     * Tests to see if getSubscribedStripes becomes 16 when join called
     * until no more can be joined 
     * Succeeds: if the number of  subscribed stripes equals total stripes 
     */
    while(getChannel().joinAdditionalStripe(this) != null){}
    if(getChannel().getSubscribedStripes().size() == getChannel().getNumStripes()){
      System.out.println("Join Additional Stripe(all) [ PASSED ]" );
    }
    else{
      System.out.println("Join Additional Stripe(all) [ FAILED ]" );
      passed = false;
    } 

    /**
     * Tests to see if leaveStripe becomes 15 when leave called
     * Succeeds: if the number of  subscribed stripes equals 16 -1
     */
    getChannel().leaveStripe();
    if(getChannel().getSubscribedStripes().size() == 
      (getChannel().getNumStripes() - 1)){
      System.out.println("Leave Additional Stripe(one)[ PASSED ]" );
    }
    else{
      System.out.println("Leave Additional Stripe(one)[ FAILED ]" );
    }

    /**
     * Tests to see if getSubscribedStripes becomes 0 when leave called
     * until no more can be left 
     * Succeeds: if the number of  subscribed stripes equals 0 
     */
    while(getChannel().leaveStripe() != null){}
    if(getChannel().getSubscribedStripes().size() == 0){
      System.out.println("Leave Additional Stripe(all)[ PASSED ]" );
    }
    else{
      System.out.println("Leave Additional Stripe(all)[ FAILED ]" );
      passed = false;
    }

    /**
     * Tests to see if getPrimaryStripe returns a stripe sharing a prefix 
     * Succeeds: if the stripe shares the first digit 
     */
    if(
     getChannel().getPrimaryStripe().getStripeId().getDigit(31 , 4) == 
     getChannel().getSplitStream().getNodeId().getDigit(31, 4 )
      ){
      System.out.println("Get Primary Stripe          [ PASSED ]" );
    }
    else{
      System.out.println("Get Primary Stripe          [ FAILED ]" );
      System.out.println("\n Node Id = " + getChannel().getSplitStream().getNodeId());
      System.out.println("Stripe id = " + getChannel().getPrimaryStripe().getStripeId());
      passed = false;
    }

    System.out.println("");
    
    if(passed){
      System.out.println("Channel Unit Test           [ PASSED ] ");
    }
    else{
      System.out.println("Channel Unit Test           [ FAILED ] ");
    }
    return passed;
  }

  public Channel getChannel(){
    return channel;
  }
  
  public void setChannel(Channel channel){
    this.channel = channel;
  }


  public Channel createChannel(){

        int base = RoutingTable.baseBitLength();
	Channel c = 
           ((ISplitStream) splitStreamNodes.elementAt(0)).createChannel(1<<base,"ChannelUnitTest");
	while(simulate());
	return c;

  }
 
  public void init(){

       simulator = new EuclideanNetwork();
      idFactory = new RandomNodeIdFactory();
      factory = new DirectPastryNodeFactory(idFactory, simulator);
      rng = new Random(5);
      pastrynodes = new Vector();
      splitStreamNodes = new Vector();

  }
  protected void createNodes() {
    for (int i=0; i < numNodes; i++) {
      makeNode();
      while(simulate());
    }
    while(simulate());
  }

  protected void makeNode() {
    PastryNode pn = factory.newNode(getBootstrap());
    Scribe scribe = new Scribe(pn, credentials);
    ISplitStream ss = new SplitStreamImpl(pn, scribe);
    splitStreamNodes.add(ss);
    pastrynodes.add(pn);
    this.scribe = scribe;

  }

  private NodeHandle getBootstrap() {
	NodeHandle bootstrap = null;
	try {
	    PastryNode lastnode = (PastryNode) pastrynodes.lastElement();
	    bootstrap = lastnode.getLocalHandle();
	} catch (NoSuchElementException e) {
	}
	return bootstrap;
  }

  public boolean simulate() { 
	return simulator.simulate(); 
  }
  public void update(Observable o, Object data){
     /* do nothing */
  }
}
