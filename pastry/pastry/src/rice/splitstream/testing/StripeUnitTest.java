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
 * This test determines whether the Stripe class is functioning as it is
 * supposed to be.
 *
 * @author Ansley Post
 *
 * @deprecated This version of SplitStream has been deprecated - please use the version
 *   located in the rice.p2p.splitstream package.
 */
public class StripeUnitTest implements Observer {

 private EuclideanNetwork simulator;
 private DirectPastryNodeFactory factory;
 private Vector splitStreamNodes;
 private Credentials credentials = new PermissiveCredentials();
 private Vector pastrynodes;
 private static int numNodes = 50;
 private Random rng;
 private RandomNodeIdFactory idFactory;
 private Channel channel;
 private Stripe stripe;

 public static void main(String argv[]){
      StripeUnitTest test = new StripeUnitTest();
      test.run();
 } 

  public boolean run(){
      init();
      createNodes();
      setChannel(createChannel());
      setStripe(getChannel().joinAdditionalStripe(this));
      return(testStripe());
  }
  public boolean testStripe(){
    boolean passed = true;
    System.out.println("");

    if(getStripe().getChannel().equals(getChannel())){
      System.out.println("Get Channel                 [ PASSED ] ");
    }
    else{
      System.out.println("Get Channel                 [ FAILED ] ");
      passed = false;
    }

    if(getStripe().getStripeId() != null){
      System.out.println("Get StripeId                [ PASSED ] ");
    }
    else{
      System.out.println("Get StripeId                [ FAILED ] ");
      passed = false;
    }

    if(getStripe().getOutputStream() != null){
      System.out.println("Get OutputStream            [ PASSED ] ");
    }
    else{
      System.out.println("Get OutputStream            [ FAILED ] ");
      passed = false;
    }

    if(getStripe().getInputStream() != null){
      System.out.println("Get InputStream             [ PASSED ] ");
    }
    else{
      System.out.println("Get InputStream             [ FAILED ] ");
      passed = false;
    }

    if(getStripe().getState() == Stripe.STRIPE_SUBSCRIBED){
      System.out.println("Get State (Subscribed)      [ PASSED ] ");
    }
    else{
      System.out.println("Get State (Subscribed)      [ FAILED ] ");
      passed = false;
    }

    System.out.println("");
    if(passed){
      System.out.println("Stripe Unit Test            [ PASSED ] ");
    }
    else{
      System.out.println("Stripe Unit Test            [ FAILED ] ");
    }
    return passed;
  }

  public Stripe getStripe(){
    return stripe;
  }
  
  public void setStripe(Stripe stripe){
    this.stripe = stripe;
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

  public void update(Observable o, Object arg){
  
  }
}
