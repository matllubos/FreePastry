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
 * It is the basic unit in the system. It is rooted at the stripeId
 * for the stripe.  It is through the stripe that data is sent. It
 * can be subscribed to in which case data is recieved or it can
 * be unsubscribed. A stripe can have some number of children
 * and controlling this is the way that bandwidth is controlled.
 * If a stripe is dropped then it searches for a new parent. 
 * A stripe recieves all the scribe messages for the topicId
 * It handles subscribtions and data received through scribe
 *
 * @version $Id$
 * @author Ansley Post
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
    * Currently this a byteArrayInputStream but should be
    * converted to a SplitStreamInputStream so that the
    * buffer will not grow forever. Currently the app
    * does not use this stream for input and instead
    * uses the Observer parent
    */
   private InputStream inputStream = null;

   /**
    * The buffer that holds the data, it is used by 
    * the input stream. Which is currently not used
    */
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
   private Vector root_path = new Vector();

   /**
    * The constructor used when creating a stripe from scratch.
    *
    * @param stripeId the stripeId that this stripe will be rooted at
    * @param channel the channel this stripe belongs to
    * @param scribe the underlying scribe object used
    * @param credentials the security credentials used
    * @param create wheter this stripe has already been created or needs
    * to be created now
    */
   public Stripe(StripeId stripeId, Channel channel, IScribe scribe, Credentials credentials, boolean create){
      this.scribe = scribe;
      this.credentials = credentials;
      this.channel = channel;
      this.stripeId = stripeId;

      if(create){
      	if(scribe.create(stripeId, credentials)){
      	}
      }
	stripeState = STRIPE_UNSUBSCRIBED;
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
      * This causes us to stop getting data and to leave the
      * scribe topic group 
      */
     public void leaveStripe(){
       stripeState = STRIPE_UNSUBSCRIBED;
       scribe.leave(stripeId, this, credentials);
     }

     /**
      * Joins this stripe 
      * It causes this stripe to become subscribed and allows
      * data to start being received
      */
     public void joinStripe(){
	if(scribe.join(stripeId, this, credentials)){
 		stripeState = STRIPE_SUBSCRIBED;
		inputStream = new ByteArrayInputStream(inputBuffer);
	}
     }

     /**
      * Method called when this stripe is dropped
      */
     public void dropped(){
        stripeState = STRIPE_DROPPED;
     }

   /**
    * get the state of the Stripe 
    * @return int the State the stripe is in 
    */
    public int getState(){ 
       return stripeState;
    }


    /**
     * Scribe Implementation Methods
     */
     
     /**
      * The fault handler upcall generated by scribe upon a fault
      * Not overridden since we rely on the underlying scribe faulthandling
      *
      * @param msg the msg sent upon fault
      * @param faultyParent the parent that has become faulty
      */
     public void faultHandler(ScribeMessage msg, NodeHandle faultyParent){}
  
     /**
      * Upcall generate when a message is forwarded
      *
      * @param msg the message being forwarded
      */
     public void forwardHandler(ScribeMessage msg){}
       
     /**
      * Upcall generated when a message is received
      * In this case it means that new data has come for the stripe
      *
      */
     public void receiveMessage(ScribeMessage msg){
       setChanged();
       /* send the data to the application - Atul*/
       notifyObservers( msg.getData()); 

       /* Check the type of message */
       /* then make call accordingly */
     }
      
     /**
      * Upcall generated when the underlying scribe layer is ready
      *
      */
     public void scribeIsReady(){}

     /**
      * Upcall generated when a new subscriber is added/removed
      * This is the most complex method in the stripe. It controls
      * if a stripe will take on a child and the logic associated
      * with dropping a child. It also must detect when a child has
      * unsubscribed from the topic. 
      *
      * @param topicId the topic that is being subscribed/unsubscribed
      * @param child the NodeHandle of the child joining
      * @param wasAdded wheter a child was subscribed/unsubscribed
      * @param data the date contianed in the subscribe/unsubscribe message
      */  
     public void subscribeHandler(NodeId topicId,
                                  NodeHandle child, boolean wasAdded, Serializable data){
	    BandwidthManager bandwidthManager = getChannel().getBandwidthManager();

	    if(wasAdded){
		//System.out.println("Child was added ");
		if(bandwidthManager.canTakeChild(getChannel())){
		    channel.stripeSubscriberAdded();
		    Credentials credentials = new PermissiveCredentials();
		    Vector child_root_path = root_path;
		    child_root_path.add( ((Scribe)scribe).getLocalHandle() );
		    channel.routeMsgDirect( child, new ControlPropogatePathMessage( channel.getAddress(),
										    channel.getNodeHandle(),
										    topicId,
										    credentials,
										    child_root_path ),
					    credentials, null );
		}
		else{
		    /* THIS IS WHERE THE DROP SHOULD OCCUR */
		    Credentials credentials = new PermissiveCredentials();
		    channel.routeMsgDirect( child, new ControlDropMessage( channel.getAddress(),
									   channel.getNodeHandle(),
									   topicId,
									   credentials,
									   channel.getSpareCapacityId(), channel.getChannelId() ),
					    credentials, null );
		    scribe.removeChild(child, topicId);
		    //bandwidthManager.additionalBandwidthFreed(channel);
		    //System.out.println("SHOULD NOT TAKE CHILD");
		}
		/* We should check if we can take this child on */
	    }
	    else {
		// child was dropped
		//System.out.println("Child was dropped ");
		//bandwidthManager.additionalBandwidthFreed(channel);
	    }
     }

    /** 
     * The constant status code associate with the subscribed state
     */
    public static final int STRIPE_SUBSCRIBED = 0;

    /** 
     * The constant status code associate with the unsubscribed state
     */
    public static final int STRIPE_UNSUBSCRIBED = 1;

    /** 
     * The constant status code associate with the dropped state
     */
    public static final int STRIPE_DROPPED = 2;
}

