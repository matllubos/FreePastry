package rice.splitstream;
import rice.splitstream.messaging.*;
import rice.scribe.*;
import rice.scribe.messaging.*;
import rice.pastry.*;
import rice.pastry.standard.*;
import rice.pastry.security.*;
import java.io.*; 
import java.util.*;
/**
 * This class encapsulates all data about an individual stripe.
 */
public class Stripe extends Observable implements IScribeApp{
   /**
    * The stripe state, wheter it is dropped, connected, etc.
    */
   private int stripeState = 0;
   /**
    * The stripeId for this stripe
    */
   private StripeId stripeId = null;
   /**
    * The channel this stripe is a part of.
    */
   private Channel channel =  null;
   /**
    * The input stream used to get data from this stripe.
    */
   private InputStream inputStream = null;
   private byte[] inputBuffer = new byte[1];  
   /**
    * The output stream used to give date to this stripe.
    */
   private OutputStream outputStream = null;
   /**
    * The credentials for this node. Not currently used.
    * Here as a place holder.
    */
   private Credentials credentials = null;
   /** 
    * The scribe instance used by this stripe.
    */
   private IScribe scribe = null;
   /**
    * A flag wheter or not this stripe is the primary stripe for this node
    */
   private boolean isPrimary = false;  

   /**
    * The path from this node to the root
    */
   private Vector root_path = null;

   /**
    * The constructor used when creating a stripe from scratch.
    */
   public Stripe(StripeId stripeId, Channel channel, IScribe scribe, Credentials credentials, boolean create){
      this.scribe = scribe;
      this.credentials = credentials;
      this.channel = channel;
      this.stripeId = stripeId;

      if(create){
      	if(scribe.create(stripeId, credentials)){
		stripeState = STRIPE_UNSUBSCRIBED;
      	}
      }
	//System.out.println("Stripe Created");
	outputStream = new SplitStreamOutputStream(stripeId, scribe, credentials);
    }

   /**
    * Sets the path to root for this stripe
    * @param path The path to the root in the form of a vector of NodeHandles
    */
   public void setRootPath( Vector path )
   {
      root_path = path;
   }
   /**
    * Returns the path to root for this stripe
    * @return The path to the root in the form of a vector of NodeHandles
    */
   public Vector getRootPath()
   {
      return root_path;
   }

   /**
    * gets the StripeID for this stripe
    * @return theStripeID 
    */
    public StripeId getStripeId(){
     return stripeId;
    }
    /**
    * gets the Channel that this stripe is a part of 
    * @return Channel Object 
    */
    public Channel getChannel(){
      return channel;
    }
    /**
     * Gets the OutputStream for this stripe.
     * @return StripeOutputStream for this stripe
     */
    public OutputStream getOutputStream(){
      return outputStream;
    }
    /**
     * Gets the InputStream for this Stripe.
     * @return StripeInputStream for this Stripe
     */
     public InputStream getInputStream(){
       return inputStream;
     }
     /**
      * Leaves this stripe
      */
     public void leaveStripe(){
       stripeState = STRIPE_UNSUBSCRIBED;
     }
     public void joinStripe(){
	if(scribe.join(stripeId, this, credentials)){
 		stripeState = STRIPE_SUBSCRIBED;
		inputStream = new ByteArrayInputStream(inputBuffer);
	}
     }	
     public void backdoorSend(Serializable data){
	scribe.multicast(stripeId, data, credentials);
     }
    /**
    * get the state of the Stripe 
    * @return int s
    */
    public int getState(){ 
       return stripeState;
    }
    /**
     * Scribe Implementation Methods
     */

     public void faultHandler(ScribeMessage msg, NodeHandle faultyParent){}
     public void forwardHandler(ScribeMessage msg){}
     public void receiveMessage(ScribeMessage msg){
       setChanged();
       notifyObservers(); 

       /* Check the type of message */
       /* then make call accordingly */
     }
     public void scribeIsReady(){}
     public void subscribeHandler(NodeId topicId,
                                  NodeHandle child, boolean wasAdded, Serializable data){
	    BandwidthManager bandwidthManager = getChannel().getBandwidthManager();
	    if(bandwidthManager.canTakeChild(getChannel(), this)){
	        channel.stripeSubscriberAdded();
            }
	    else{
                /* THIS IS WHERE THE DROP SHOULD OCCUR */
                Credentials credentials = new PermissiveCredentials();
                channel.routeMsgDirect( child, new ControlDropMessage( channel.getAddress(),
                                                                       channel.getNodeHandle(),
                                                                       topicId,
                                                                       credentials,
                                                                       channel.getSpareCapacityId() ),
                                        credentials, null );
                //System.out.println("SHOULD NOT TAKE CHILD");
	   }
	/* We should check if we can take this child on */
     }

    public static final int STRIPE_SUBSCRIBED = 0;
    public static final int STRIPE_UNSUBSCRIBED = 1;
    public static final int STRIPE_DROPPED = 2;
}
