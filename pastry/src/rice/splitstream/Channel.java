package rice.splitstream;
import rice.pastry.standard.*;
import rice.scribe.*;
import rice.pastry.security.*;
import rice.pastry.*;
public class Channel {
   /**
    * ChannelId for this channel 
    */
   private ChannelId channelId = null;
   /**
    * The number of stripes in this channel the node is currently
    * subscribed to.
    */
   private int subscribedStripes = 0;
   /**
    * The array of all subscribed stripes
    */
   private Stripe[] stripes = null;
   /**
    * The stripeIds for all stripes in this channel.
    */
   private StripeId[] stripeIds = null;
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
   public Channel(int numStripes, IScribe scribe, Credentials cred, BandwidthManager bandwidthManager){

        this.subscribedStripes = numStripes;
	this.scribe = scribe;
	this.bandwidthManager = bandwidthManager;
	/* register this channel with the bandwidthManager */
	this.bandwidthManager.registerChannel(this);
        NodeId topicId = (new RandomNodeIdFactory()).generateNodeId();
        if(scribe.create(topicId, cred)){
		isReady = true;
		this.channelId = (ChannelId) topicId;
        } 		
	stripes = new Stripe[numStripes];
        stripeIds = new StripeId[numStripes];
	for(int i = 0; i < numStripes; i++){
		stripes[i] = new Stripe(this, scribe, cred);
		stripeIds[i] = stripes[i].getStripeId();
	}
        /* Send a create message to the node with responsible with the stripes*/
   }

   /**
    * Constructor to create a Channel when a channelID is known
    */ 
   public Channel(ChannelId channelId, IScribe scribe, BandwidthManager bandwidthManager){
	this.channelId = channelId;
	this.bandwidthManager = bandwidthManager;
	this.scribe = scribe;
        /* Send out a message to the scribe group */
        /* Get back all the StripeID's and then process them */
        /* Then we should be attached */
 

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
 
  /** 
   * A channel consists of a number of stripes. This number is determined
   * at the time of content creation. Note that a content receiver does not
   * necessarily need to receive all stripes in order to view the content.
   * @return An array of all StripeIds associated with this channel
   */ 
  public StripeId[] getStripes(){
	return stripeIds;
  }

  /**
   * At any moment a node is subscribed to at least 1 but possibly
   * more stripes. They will always be subscribed to thier primary
   * Stripe.
   * @return Stripe[] the Stripes this node is subscribed to.
   */
  public Stripe[] getSubscribedStripes(){
       return  stripes;
  }

  /**
   * The primary stripe is the stripe that the user must have.
   * @return Stripe The Strip object that is the primary stripe.
   */ 
  public Stripe getPrimaryStripe(){
   /** 
    * GO THROUGH AND LOOK AT THE STRIPES AND FIGURE OUT WHICH
    * ONE IS THE PRIMARY
    */
    return null;
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
  public Stripe joinStripe(StripeId stripeId){
	return null;
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
     return stripeIds.length;
  }
}








