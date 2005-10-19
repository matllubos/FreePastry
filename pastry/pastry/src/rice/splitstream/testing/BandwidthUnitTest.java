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
 * This test determines wheter the bandwidthManager is functioning as it is
 * supposed to be.
 *
 * @author Ansley Post
 *
 * @deprecated This version of SplitStream has been deprecated - please use the version
 *   located in the rice.p2p.splitstream package.
 */
public class BandwidthUnitTest{

 private EuclideanNetwork simulator;
 private DirectPastryNodeFactory factory;
 private Vector splitStreamNodes;
 private Credentials credentials = new PermissiveCredentials();
 private Vector pastrynodes;
 private static int numNodes = 50;
 private Random rng;
 private RandomNodeIdFactory idFactory;
 private Channel channel;
 private BandwidthManager bandwidthManager;

 public static void main(String argv[]){
      BandwidthUnitTest test = new BandwidthUnitTest();
      test.run();
 } 

  public boolean run(){
      init();
      createNodes();
      setChannel(createChannel());
      setBandwidthManager(getChannel().getBandwidthManager());
      return(testBandwidthManager());
  }
  public boolean testBandwidthManager(){
    boolean passed = true;
    System.out.println("");

    /**
     * Tests to see if Get/Set Default bandwidth functions correctly 
     * Succeeds: if getBandwidth() returns the value that setBandwidth()
     * was set with.  
     */
    getBandwidthManager().setDefaultBandwidth(50);
    if(getBandwidthManager().getDefaultBandwidth() == 50){
      System.out.println("Get/Set Default Bandwidth   [ PASSED ] ");
    }
    else{
      System.out.println("Get/Set Default Bandwidth   [ FAILED ] ");
      passed = false;
    }

    /**
     * Tests to see if AdjustBandwidth/GetMaxBandwidth functions correctly 
     * Succeeds: if getMaxBandwidth() returns the value that AdjustBandwidth()
     * was called with
     */
    getBandwidthManager().adjustBandwidth(getChannel(), 50);
    if(getBandwidthManager().getMaxBandwidth(getChannel()) == 50){
      System.out.println("Adjust/Get Max Bandwidth    [ PASSED ] ");
    }
    else{
      System.out.println("Adjust/Get Max Bandwidth    [ FAILED ] ");
      passed = false;
    }

    /**
     * Tests to see if additonalBandwidth/getBandwidth functions correctly 
     * Succeeds: if additionalBandwidthUsed increases the value of getBandwidth 
     * by 1 
     */
    getBandwidthManager().additionalBandwidthUsed(getChannel());
    if(getBandwidthManager().getUsedBandwidth(getChannel()) == 1){
      System.out.println("Additional Bandwidth Used   [ PASSED ] ");
      System.out.println("Bandwidth Used              [ PASSED ] ");
    }
    else{
      System.out.println("Additional Bandwidth Used   [ FAILED ] ");
      System.out.println("Bandwidth Used              [ FAILED ] ");
      passed = false;
    }

    /**
     * Tests to see if additonalBandwidthFreed functions correctly 
     * Succeeds: if additionalBandwidthFreed decreases the value of 
     * getBandwidth by 1 
     */
    getBandwidthManager().additionalBandwidthFreed(getChannel());
    if(getBandwidthManager().getUsedBandwidth(getChannel()) == 0){
      System.out.println("Additional Bandwidth Freed  [ PASSED ] ");
    }
    else{
      System.out.println("Additional Bandwidth Freed  [ FAILED ] ");
      passed = false;
    }

    /**
     * Tests to see if canTakeChild functions correctly when a child
     * can be taken
     * Succeeds: if canTakeChild returns true 
     */
    if(getBandwidthManager().canTakeChild(getChannel())){
      System.out.println("Can Take Child (empty)      [ PASSED ] ");
    }
    else{
      System.out.println("Can Take Child (empty)      [ FAILED ] ");
      passed = false;
    }

    /**
     * Tests to see if canTakeChild stops returning true after
     * all bandwidth is allocated. 
     * Succeeds: if canTakeChild returns false 
     */
    while(getBandwidthManager().canTakeChild(getChannel())){
       getBandwidthManager().additionalBandwidthUsed(getChannel());
    }
    if(getBandwidthManager().getUsedBandwidth(getChannel()) == 
       getBandwidthManager().getMaxBandwidth(getChannel())){
      System.out.println("Can Take Child (full)       [ PASSED ] ");
    }
    else{
      System.out.println("Can Take Child (full)       [ FAILED ] ");
      passed = false;
    }
    
    /**
     * Tests to see if canTakeChild returnings true after
     * some bandwidth is freed. 
     * Succeeds: if canTakeChild returns true 
     */
    getBandwidthManager().additionalBandwidthFreed(getChannel());
    if(getBandwidthManager().canTakeChild(getChannel())){
      System.out.println("Can Take Child (nonfull)    [ PASSED ] ");
    }
    else{
      System.out.println("Can Take Child (nonfull)    [ FAILED ] ");
      passed = false;
    }

    System.out.println("");
    if(passed){
      System.out.println("Bandwidth Unit Test         [ PASSED ] ");
    }
    else{
      System.out.println("Bandwidth Unit Test         [ FAILED ] ");
    }
    return passed;
  }

  public Channel getChannel(){
    return channel;
  }
  
  public void setChannel(Channel channel){
    this.channel = channel;
  }

  public BandwidthManager getBandwidthManager(){
    return bandwidthManager;
  }

  public void setBandwidthManager(BandwidthManager bandwidthManager){
    this.bandwidthManager = bandwidthManager;
  }

  public Channel createChannel(){

        int base = RoutingTable.baseBitLength();
	Channel c = 
           ((ISplitStream) splitStreamNodes.elementAt(0)).createChannel(1<<base,"BandwidthUnitTest");
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

}
