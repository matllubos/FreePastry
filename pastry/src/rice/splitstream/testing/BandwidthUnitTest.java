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
      System.out.println("Bandwidth Test Program v0.1");
      BandwidthUnitTest test = new BandwidthUnitTest();
      test.init();
      test.createNodes();
      test.setChannel(test.createChannel());
      test.setBandwidthManager(test.getChannel().getBandwidthManager());
      test.testBandwidthManager();
 } 

  public void testBandwidthManager(){
    boolean passed = true;
    System.out.println("");
    getBandwidthManager().setDefaultBandwidth(50);
    if(getBandwidthManager().getDefaultBandwidth() == 50){
      System.out.println("Get/Set Default Bandwidth   [ PASSED ] ");
    }
    else{
      System.out.println("Get/Set Default Bandwidth   [ FAILED ] ");
      passed = false;
    }


    getBandwidthManager().adjustBandwidth(getChannel(), 50);
    if(getBandwidthManager().getMaxBandwidth(getChannel()) == 50){
      System.out.println("Adjust/Get Max Bandwidth    [ PASSED ] ");
    }
    else{
      System.out.println("Adjust/Get Max Bandwidth    [ FAILED ] ");
      passed = false;
    }

     

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

    getBandwidthManager().additionalBandwidthFreed(getChannel());
    if(getBandwidthManager().getUsedBandwidth(getChannel()) == 0){
      System.out.println("Additional Bandwidth Freed  [ PASSED ] ");
    }
    else{
      System.out.println("Additional Bandwidth Freed  [ FAILED ] ");
      passed = false;
    }

    if(getBandwidthManager().canTakeChild(getChannel())){
      System.out.println("Can Take Child (empty)      [ PASSED ] ");
    }
    else{
      System.out.println("Can Take Child (empty)      [ FAILED ] ");
      passed = false;
    }

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

	System.out.println("Attempting to create a Channel");
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
    System.out.println("All Nodes Created Succesfully");
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
