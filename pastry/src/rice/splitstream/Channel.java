package rice.splitstream;

import java.io.Serializable;
import java.util.*;

import rice.scribe.*;
import rice.scribe.messaging.*;

import rice.pastry.*;
import rice.pastry.client.*;
import rice.pastry.messaging.*;
import rice.pastry.standard.*;
import rice.pastry.security.*;

import rice.splitstream.messaging.*;

/**
 * This is the channel object that represents a group of stripes in
 * SplitStream.
 *
 * @author Ansley Post
 */
public class Channel extends PastryAppl implements IScribeApp {
   /**
    * ChannelId for this channel 
    */
   private ChannelId channelId = null;
   /**
    * The number of stripes in this channel the node is currently
    * subscribed to.
    */
   private SpareCapacityId spareCapacityId = null;
   private int numSubscribedStripes = 0;
   private Hashtable stripeIdTable = new Hashtable();
   private Vector subscribedStripes = new Vector();
   /**
    * The primary stripe for this node.
    */
   private int numStripes = 0;
   private Stripe primaryStripe = null;
   /**
    * The total number of children on all stripes
    * this node will support.
    */
   private int outChannel = 0;
   /**
    * The instance of Scribe that this channel will use
    * for messaging.
    */
   private IScribe scribe = null;
   /**
    * The credentials for this node, currently not used.
    * Always set to null. Here as a placeholder 
    */
   private Credentials cred = null;
   private boolean isReady = false;
   private static Address address = SplitStreamAddress.instance();
   /**
    * The bandwidth manager for this channel, responsible for 
    * keeping track of the number of children, and then deciding
    * when to take on children.
    */
   private BandwidthManager bandwidthManager = null;

   /**
    * Constructor to Create a new channel
    *
    */
   public Channel(int numStripes, IScribe scribe, Credentials cred, 
                  BandwidthManager bandwidthManager, PastryNode node){
 	super(node);	
        this.numSubscribedStripes = numStripes;
	this.scribe = scribe;
	this.bandwidthManager = bandwidthManager;
	this.numStripes = numStripes;
	PastrySeed.setSeed((int)System.currentTimeMillis());
	RandomNodeIdFactory random = new RandomNodeIdFactory();
	/* register this channel with the bandwidthManager */
	this.bandwidthManager.registerChannel(this);
	scribe.registerApp(this);
        NodeId topicId = random.generateNodeId();
        if(scribe.create(topicId, cred)){
		System.out.println("Channel Topic Created");
		this.channelId = new ChannelId(topicId);
        }
        topicId = random.generateNodeId();
        if(scribe.create(topicId, cred)){
		this.spareCapacityId = new SpareCapacityId(topicId);
        }
        NodeId baseId = random.generateNodeId();
	for(int i = 0; i < this.numStripes; i++){
		StripeId stripeId = new StripeId(baseId.getAlternateId(numStripes, 4, i)); 
		Stripe stripe = new Stripe(stripeId, this, scribe,cred,true);
		stripeIdTable.put(stripeId, stripe);
	/*	if(stripeId.getDigit(getRoutingTable().numRows() -1, 4) 
		    == getNodeId().getDigit(getRoutingTable().numRows() -1,4))
			primaryStripe = stripe; 
	*/
        primaryStripe = stripe;	
	}
	//primaryStripe.joinStripe(observer);
        /* Also select the primary stripe */
   	NodeId[] subInfo = new NodeId[this.numStripes + 2]; 
	subInfo[0] = channelId;
	for(int i = 1; i < subInfo.length -1; i++){
		subInfo[i] = getStripes()[i - 1];
	}
	subInfo[subInfo.length-1] = spareCapacityId;
	if(scribe.join(channelId, this, cred, subInfo)){
		System.out.println("Creator Joined Group" + getNodeId());
	}		
	if(scribe.join(spareCapacityId, this, cred, subInfo)){
		System.out.println("Creator Joined Spare Capacity Group" + getNodeId());
	}		
   	isReady = true;
   }

   /**
    * Constructor to create a Channel when a channelID is known
    */ 
   public Channel(ChannelId channelId, IScribe scribe, Credentials cred, 
                  BandwidthManager bandwidthManager, PastryNode node){
	
 	super(node);	
	this.channelId = channelId;
	this.bandwidthManager = bandwidthManager;
	this.scribe = scribe;
        scribe.registerApp(this);
	ControlAttachMessage attachMessage = new ControlAttachMessage();
        System.out.println("Sending Anycast Message from " + getNodeId());
        scribe.anycast(channelId, attachMessage, cred );

   }
  
  public Channel(ChannelId channelId, StripeId[] stripeIds, SpareCapacityId 
                 spareCapacityId, IScribe scribe, BandwidthManager bandwidthManager, PastryNode node){

 	super(node);
	this.channelId = channelId;
	this.spareCapacityId = spareCapacityId;

	for(int i = 0 ; i < stripeIds.length ; i++){
		if(stripeIdTable == null) {System.out.println("NULL");}
		stripeIdTable.put(stripeIds[i], "NULL");
		
	}

	this.numStripes = stripeIds.length;
	this.scribe = scribe;
	this.bandwidthManager = bandwidthManager;
	if(scribe.join(channelId, this, cred)){
	}
	
	if(scribe.join(spareCapacityId, this, cred)){
	}		
	/* Subscribe to a primary stripe */
	isReady = true;
	System.out.println("A Channel Object is being created (In Path) at " + getNodeId());
    }
 
  /**
   * Channel Object is responsible for managing local node's usage of
   * outgoing bandwidth and incoming bandwidth, which is indicated by number
   * of stripes the local node has tuned to.
   * @param int The outgoing bandwidth 
   * The incoming bandwidth is assumed from the outgoing.
   *
   */
  public void configureChannel(int outChannel){
    this.outChannel = outChannel;
    bandwidthManager.adjustBandwidth(this, outChannel); 
  }
  public ChannelId getChannelId(){
	return channelId;
  } 
  /** 
   * A channel consists of a number of stripes. This number is determined
   * at the time of content creation. Note that a content receiver does not
   * necessarily need to receive all stripes in order to view the content.
   * @return An array of all StripeIds associated with this channel
   */ 
  public StripeId[] getStripes(){
	Object[] obj = stripeIdTable.keySet().toArray();	
	StripeId[] temp = new StripeId[obj.length];
	for(int i = 0; i < obj.length; i++){
		temp[i] = (StripeId) obj[i];
	}
	
	return (temp);
  }

  /**
   * At any moment a node is subscribed to at least 1 but possibly
   * more stripes. They will always be subscribed to thier primary
   * Stripe.
   * @return Stripe[] the Stripes this node is subscribed to.
   */
  public Vector getSubscribedStripes(){
 	return(subscribedStripes); 
  }

  /**
   * The primary stripe is the stripe that the user must have.
   * @return Stripe The Stripe object that is the primary stripe.
   */ 
  public Stripe getPrimaryStripe(){
    return primaryStripe;
  }

  /**
   * Returns whether the channel is currently ready 
   * @return boolean State of the channel 
   */
  public boolean isReady(){return isReady;}

  /**
   * Returns a random stripe from this channel that the user is not
   * currently subscribed to
   * @return Stripe A random stripe
   */
  public Stripe joinAdditionalStripe(Observer observer ){
	if(getNumSubscribedStripes() == getNumStripes())
		return null;
	boolean found= false;
	Stripe toReturn = null;
        for(int i = 0 ; i < getStripes().length && !found; i ++){
	  	if(stripeIdTable.get(getStripes()[i]) instanceof String){
			toReturn = joinStripe(getStripes()[i], observer);
			found = true;
		}
	}
	return toReturn;
				
  }

  /**
   * Join a specific Stripe of this channel
   * @param stripeID The stripe to subscribe to
   * @return boolean Success of the join operation
   */ 
  public Stripe joinStripe(StripeId stripeId, Observer observer){
		Object tableEntry = stripeIdTable.get(stripeId);
		Stripe stripe = null; 
		if(tableEntry instanceof String){
		   stripe = new Stripe(stripeId, this, scribe, cred, false);
		   stripeIdTable.put(stripeId, stripe);
		}
		else{
		   stripe = (Stripe) tableEntry;
		}
		stripe.joinStripe();	
		stripe.addObserver(observer);
		subscribedStripes.addElement(stripe);
		return(stripe);
  }

 /**
  * Leave a random stripe
  * @return the stripeID left 
  */
  public StripeId leaveStripe(){return null;}
 
 /**
  * Get the number of Stripes this channel is subscribed to.
  * @return the number of Stripes
  */
  public int getNumSubscribedStripes(){
     return subscribedStripes.size();
  }

 /**
  * Get the total number of stripes in this channel
  * @return the total number of stripes
  */
  public int getNumStripes(){
     return numStripes;
  }

  public SpareCapacityId getSpareCapacityId(){
     return spareCapacityId;
  }


  public void faultHandler(ScribeMessage msg, NodeHandle faultyParent){}
  public void forwardHandler(ScribeMessage msg){}
  public void receiveMessage(ScribeMessage msg){
     /* Check the type of message */
     /* then make call accordingly */
	if(msg.getTopicId() == channelId){
	 	ControlAttachMessage attachMessage =(ControlAttachMessage) msg.getData();
		attachMessage.handleMessage(this, scribe, msg.getSource());
	}
	else if(msg.getTopicId() == spareCapacityId){
		Stripe stripe = null;
		ControlFindParentMessage parentMessage = (ControlFindParentMessage) msg;
		if(stripeIdTable.get(parentMessage.getStripeId()) instanceof Stripe){
			stripe = (Stripe) stripeIdTable.get(parentMessage.getStripeId());	
		}
		parentMessage.handleForwardWrapper((Scribe) scribe,((Scribe) scribe).getTopic(parentMessage.getTopicId()), stripe );
	}
  }
  public void scribeIsReady(){
  }
  public void subscribeHandler(NodeId topicId, 
                               NodeHandle child, boolean wasAdded, Serializable data){}

  /** -- Pastry Implementation -- **/
  public Address getAddress(){
	return address;
  }
  public Credentials getCredentials(){
	return null;
  }
  public void messageForAppl (Message msg){
	NodeId[] subInfo = (NodeId[]) ((ControlAttachResponseMessage) msg).getContent();	
	channelId = new ChannelId(subInfo[0]);
	spareCapacityId = new SpareCapacityId(subInfo[subInfo.length-1]);
	/* Fill in all instance variable for channel */
	for(int i = 1 ; i < subInfo.length-1 ; i++){
		this.numStripes = subInfo.length -2 ;
		stripeIdTable.put(new StripeId(subInfo[i]), "NULL");
	}
        if(scribe.join(channelId, this, cred, subInfo)){
	}
	if(scribe.join(spareCapacityId, this, cred, subInfo)){
	}		
	isReady = true;
  }
  public String toString(){
	String toReturn = "Channel: " + getChannelId() + "\n";
	toReturn = toReturn + "Stripes: \n";
	for(int i = 0; i < numStripes; i++){
		toReturn = toReturn + "\t" + getStripes()[i] + "\n";
	}
	toReturn = toReturn + "Spare Capacity Id: " + getSpareCapacityId();
	return(toReturn);	
  }
}
  





