package rice.splitstream;
import rice.pastry.standard.*;
import java.io.Serializable;
import java.util.*;
import rice.scribe.*;
import rice.scribe.messaging.*;
import rice.pastry.security.*;
import rice.pastry.*;
import rice.pastry.client.*;
import rice.pastry.messaging.*;
import rice.splitstream.messaging.*;
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
   private int subscribedStripes = 0;
   private Hashtable stripeIdTable = new Hashtable();
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
        this.subscribedStripes = numStripes;
	this.scribe = scribe;
	this.bandwidthManager = bandwidthManager;
	this.numStripes = numStripes;
	/* register this channel with the bandwidthManager */
	this.bandwidthManager.registerChannel(this);
	scribe.registerApp(this);
        NodeId topicId = (new RandomNodeIdFactory()).generateNodeId();
	System.out.println("Trying to create channel : " +  topicId);
        if(scribe.create(topicId, cred)){
		System.out.println("Channel Topic Created");
        }
        topicId = (new RandomNodeIdFactory()).generateNodeId();
        if(scribe.create(topicId, cred)){
		System.out.println("SpareCapacity Topic Created");
        }
	spareCapacityId = new SpareCapacityId(topicId);
        NodeId baseId = (new RandomNodeIdFactory()).generateNodeId();
	for(int i = 0; i < numStripes; i++){
		StripeId stripeId = new StripeId(baseId.getAlternateId(numStripes, 4, i));
		stripeIdTable.put(stripeId, new Stripe(stripeId, this, scribe,cred,true));
	}
        /* Send a create message to the node with responsible with the stripes*/
        /* Also select the primary stripe */
   	NodeId[] subInfo = new NodeId[numStripes + 2]; 
	subInfo[0] = topicId;
	subInfo[subInfo.length-1] = topicId;
	if(scribe.join(topicId, this, cred, subInfo)){
		System.out.println("Joined Group");
		this.channelId = new ChannelId(topicId);
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
        /* is this right? */
        /* Change the data to be the message we want to send */
        scribe.anycast(channelId, attachMessage, cred );
 	/* join */	
        /* Get back all the StripeID's and then process them */
        /* Then we should be attached */
        /* we also need to create and mark a primary stripe */ 

   }
  
  public Channel(ChannelId channelId, StripeId[] stripeIds, SpareCapacityId 
                 spareCapacityId, IScribe scribe, BandwidthManager bandwidthManager, PastryNode node){

 	super(node);	
	System.out.println("A Channel Object is being created");
	this.channelId = channelId;
	for(int i = 0 ; i < stripeIds.length ; i++){
		stripeIdTable.put(stripeIds[i], null);
	}
	this.numStripes = stripeIds.length;
	this.scribe = scribe;
	this.bandwidthManager = bandwidthManager;
	if(scribe.join(channelId, this, cred)){
	}
	
	/* Subscribe to a primary stripe */
	isReady = true;
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
	return ((StripeId[]) stripeIdTable.keySet().toArray());
  }

  /**
   * At any moment a node is subscribed to at least 1 but possibly
   * more stripes. They will always be subscribed to thier primary
   * Stripe.
   * @return Stripe[] the Stripes this node is subscribed to.
   */
  public Stripe[] getSubscribedStripes(){
       Set s = stripeIdTable.entrySet();
       s.remove(null);
       return  ((Stripe[]) s.toArray());
  }

  /**
   * The primary stripe is the stripe that the user must have.
   * @return Stripe The Strip object that is the primary stripe.
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
  public Stripe joinAdditionalStripe(){
        return null;
  }

  /**
   * Join a specific Stripe of this channel
   * @param stripeID The stripe to subscribe to
   * @return boolean Success of the join operation
   */ 
  public Stripe joinStripe(StripeId stripeId, Observer observer){
		Stripe stripe = (Stripe) stripeIdTable.get(stripeId);
		if(stripe == null){
		   stripe = new Stripe(stripeId, this, scribe, cred, false);
		}
		stripe.joinStripe();	
		stripe.addObserver(observer);
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
     return subscribedStripes;
  }

 /**
  * Get the total number of stripes in this channel
  * @return the total number of stripes
  */
  public int getNumStripes(){
     return numStripes;
  }

  public SpareCapacityId getSpareCapacityId(){
     return null;
  }


  public void faultHandler(ScribeMessage msg, NodeHandle faultyParent){}
  public void forwardHandler(ScribeMessage msg){}
  public void receiveMessage(ScribeMessage msg){
     /* Check the type of message */
     /* then make call accordingly */
	System.out.println("Recieved Message in Channel");
	ControlAttachMessage attachMessage =(ControlAttachMessage) msg.getData();
	attachMessage.handleMessage(this, scribe, msg.getSource());
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
	/* Fill in all instance variable for channel */
        if(scribe.join(channelId, this, cred, subInfo)){
	}
	isReady = true;
  }

}
  





