package rice.splitstream;
import rice.pastry.standard.*;
import rice.scribe.*;
import rice.pastry.security.*;
import rice.pastry.*;
public class Channel {

   private ChannelId channelId = null;
   private int numStripes = 0;
   private Stripe[] stripes = null;
   private int outChannel = 0;
   private IScribe scribe = null;
   private Credentials cred;
   private boolean isReady = false;

   public Channel(int numStripes, IScribe scribe, Credentials cred){
	this.numStripes = numStripes;
 	this.cred = cred;
	this.scribe = scribe;
        NodeId topicId = (new RandomNodeIdFactory()).generateNodeId();
        if(scribe.create(topicId, cred)){
		isReady = true;
		this.channelId = (ChannelId) topicId;
        } 		
	stripes = new Stripe[numStripes];
	for(int i = 0; i < numStripes; i++){
		stripes[i] = new Stripe(this, scribe, cred);
	}
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
  }
  /** 
   * A channel consists of a number of stripes. This number is determined
   * at the time of content creation. Note that a content receiver does not
   * necessarily need to receive all stripes in order to view the content.
   * @return An array of all StripeIds associated with this channel
   */ 
  public StripeId[] getStripes(){
	return null;
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
  public Stripe getPrimaryStripe(){return null;}
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

  public int getNumSubscribedStripes(){
     return 0;
  }
}








