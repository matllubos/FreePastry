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
 * be unsubscribed from. A stripe can have some number of children
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
    * The stripe state, whether it is dropped, connected, etc.
    */
   private int stripeState = 1;

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
    * Currently this is a byteArrayInputStream but should be
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
    * A flag whether or not this stripe is the primary stripe for this node
    */
   private boolean isPrimary = false;  

   /**
    * The path from this node to the root
    */
   private Vector root_path = new Vector();

    /**
     * Ignore FindParent timeout message scheduled when this stripe was dropped
     */
    private boolean ignore_timeout = true;


    public int num_fails;
    /**
     * Flag for identifying whether the most recent drop
     * of a node is because local node dropped it, not because
     * that node sent unsubscription message.
     */
    private boolean localDrop = false;

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
        else{
          System.out.println("ERROR: Failed to create Stripe ID");
        }
      }
	stripeState = STRIPE_UNSUBSCRIBED;
	outputStream = new SplitStreamOutputStream(stripeId, scribe, credentials);
    }

   /**
    * Sets the path to root for this stripe
    * @param path The path to the root in the form of a vector of NodeHandles
    */
   public void setRootPath( Vector path )
   {
       if(path ==  null)
	   root_path.removeAllElements();
       else
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
    * Sets the timeout ignore value for this stripe
    * @param ignore Messages should/should not be ignored
    */
   public void setIgnoreTimeout( boolean ignore )
   {
      ignore_timeout = ignore;
   }

   /**
    * Should a timeout message pertaining to this stripe be ignored?
    * @return The ignore timeout value for this stripe
    */
   public boolean getIgnoreTimeout()
   {
      return ignore_timeout;
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
	if(scribe.join(stripeId, this, credentials, channel.getChannelMetaData())){
 		stripeState = STRIPE_SUBSCRIBED;
		inputStream = new ByteArrayInputStream(inputBuffer);
	}
        else{
          System.out.println("ERROR: Failed to join Stripe");
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

    public void setState(int state){
	stripeState = state;
    }

    /**
     * Scribe Implementation Methods
     */
     
     /**
      * The fault handler upcall generated by scribe upon a fault
      *
      * @param msg the msg sent upon fault
      * @param faultyParent the parent that has become faulty
      */
     public void faultHandler(ScribeMessage msg, NodeHandle faultyParent){
	 /** 
	  * Get the associated data with the Channel, eg the channelId,
	  * the different stripeIds, the spare capacity ids and send it
	  * along with the subscribe message.
	  */
	 NodeId[] data = channel.getChannelMetaData();
	 msg.setData(data);
	 //System.out.println("Setting root path in faultHandler to empty at "+channel.getNodeId()+" for stripe "+getStripeId());
	 setRootPath(null);

     }
  
     /**
      * Upcall generate when a message is forwarded
      *
      * @param msg the message being forwarded
      */
     public void forwardHandler(ScribeMessage msg){}
       

    /**
     * up-call invoked by Scribe when an anycast message is being handled.
     */
    public boolean anycastHandler(ScribeMessage msg){
	boolean result = true;

	if(msg instanceof ControlFinalFindParentMessage){
	    ControlFinalFindParentMessage pmsg = (ControlFinalFindParentMessage)msg;
	    result= pmsg.handleMessage(getChannel().getSplitStream(), 
				       getChannel().getScribe(), 
				       getChannel(), 
				       this );
	}

	return result;
    }
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
      * @param wasAdded whether a child was subscribed/unsubscribed
      * @param data the data contained in the subscribe/unsubscribe message
      */  
     public void subscribeHandler(NodeId topicId,
                                  NodeHandle child, boolean wasAdded, Serializable data){
	    BandwidthManager bandwidthManager = getChannel().getBandwidthManager();
            /** 
             * Checks to see if the event that triggered this subscribe
             * handler was caused by an action at the local node. If it 
             * was we don't need to adjust the bandwidth usage.
             */
	    if(wasAdded){
		//System.out.println("STRIPE :: Child was added "+child.getNodeId()+" at "+getChannel().getNodeId()+" for "+topicId);
		if(bandwidthManager.canTakeChild(getChannel())){
		    //System.out.println("STRIPE:: can take child "+child.getNodeId()+" at "+getChannel().getNodeId()+" for "+topicId);
		    channel.stripeSubscriberAdded();
		    
		    Credentials credentials = new PermissiveCredentials();
		    Vector child_root_path = (Vector)root_path.clone();
		    child_root_path.add( ((Scribe)scribe).getLocalHandle() );
		    channel.getSplitStream().routeMsgDirect( child, new ControlPropogatePathMessage( channel.getSplitStream().getAddress(),
    channel.getSplitStream().getNodeHandle(),
    topicId,
    credentials,
    child_root_path,
    channel.getChannelId() 
  ),
    credentials, null );
		    //System.out.println("STRIPE :: Done with taking child "+getChannel().getNodeId());
		}
		else{
		    /* THIS IS WHERE THE DROP SHOULD OCCUR */
		    Credentials credentials = new PermissiveCredentials();
		    Vector ret;
		    NodeHandle victimChild;
		    StripeId victimStripeId;
		    
		    // Now, ask the bandwidth manager to free up some bandwidth, fill up
		    // victimChild and victimStripeId..
		    // XXX - might want to change how freeBandwidth returns :-)
		    ret = bandwidthManager.freeBandwidth(channel);
		    victimChild = (NodeHandle)ret.elementAt(0);
		    victimStripeId = (StripeId)ret.elementAt(1);

		    //System.out.println("STRIPE :: victimChild "+victimChild.getNodeId()+" for stripe "+victimStripeId);

		    Stripe victimStripe = channel.getStripe(victimStripeId);
		    Vector child_root_path = (Vector)getRootPath().clone();

		    child_root_path.add( ((Scribe)scribe).getLocalHandle() );

		    /**
		     * In all cases except the case that victimChild is the same as the recently added
		     * child and also for the same stripe, then we need to send the propogate path
		     * message to recently added child.
		     */
		    if(!(victimChild.getNodeId().equals(child.getNodeId()) &&
				topicId.equals((NodeId)victimStripeId))){
			channel.getSplitStream().routeMsgDirect( child, new ControlPropogatePathMessage( channel.getSplitStream().getAddress(),
	channel.getSplitStream().getNodeHandle(),
	topicId,
        credentials,
	child_root_path,
        channel.getChannelId() ),
						credentials, null );
			//System.out.println("Sending PROPOGATE message to" +child.getNodeId()+ " for stripe "+topicId);
		    }

		    channel.getSplitStream().routeMsgDirect( victimChild, new ControlDropMessage( channel.getSplitStream().getAddress(),
										     channel.getSplitStream().getNodeHandle(),
										     victimStripeId,
										     credentials,
										     channel.getSpareCapacityId(),
										     channel.getChannelId(),
     channel.getTimeoutLen())
     , credentials, null ); 
		    //System.out.println(" STRIPE "+this+" Sending DROP message to "+victimChild.getNodeId()+" for stripe"+victimStripeId+ " at "+((Scribe)scribe).getNodeId());

		    victimStripe.setLocalDrop(true);
		    scribe.removeChild(victimChild, (NodeId)victimStripeId);
		}
	    }
	    else {
		// child was dropped
		//System.out.println("STRIPE "+this+" ::Child was removed "+child.getNodeId()+" at "+getChannel().getNodeId()+ " for stripe "+getStripeId());
		if(!localDrop)
		    channel.stripeSubscriberRemoved();
		else {
		    localDrop = false;
		}
	    }
     }

    /**
     * Set whether child of this stripe was locally
     * dropped.
     */
    public void setLocalDrop(boolean value){
	localDrop = value;
    }

    /**
     * Gets whether child was dropped locally for this stripe.
     */
    public boolean getLocalDrop(){
	return localDrop;
    }

    /** 
     * The constant status code associated with the subscribed state
     */
    public static final int STRIPE_SUBSCRIBED = 0;

    /** 
     * The constant status code associated with the unsubscribed state
     */
    public static final int STRIPE_UNSUBSCRIBED = 1;

    /** 
     * The constant status code associated with the dropped state
     */
    public static final int STRIPE_DROPPED = 2;

    /**
     * A node was locally added, in response to FindParentMessage through the
     * spare capacity tree.
     */
    private boolean localChildAdded = false;
}


