package rice.splitstream;

import java.io.*;
import java.util.*;

import rice.scribe.*;
import rice.scribe.messaging.*;

import rice.pastry.*;
import rice.pastry.client.*;
import rice.pastry.messaging.*;
import rice.pastry.security.*;

import rice.splitstream.messaging.*;

/**
 * This is the implementing class of the ISplitStream interface. It 
 * provides the functionality of creating and attaching to channels.
 * It also provides a lot of implementation details. It handles the
 * creation of Channel objects in the path of the Channel tree. It 
 * also monitors the creation of stripes interior to the tree and
 * keeps track of the bandwidth used until the user subscribes to 
 * the channel. It implements the IScribeApp interface for this reason.
 * Since it only need to keep track of these numbers it does not need 
 * to implement the entire IScribeApp interface only the subscribe handler.
 *
 * @(#) SplitStreamImpl.java
 * @version $Id$
 * @author Ansley Post
 */
public class SplitStreamImpl extends PastryAppl implements ISplitStream, 
                             IScribeApp, IScribeObserver
{
    /**
     * The scribe instance for this SplitStream Object
     */
    private IScribe scribe = null;

    /**
     * The bandwidthManger which controls bandwidth usage
     */
    private BandwidthManager bandwidthManager = new BandwidthManager();

    /**
     * The pastry address of this application
     */
    private static Address address = SplitStreamAddress.instance();

    /**
     * Credentials for this application
     */
    private Credentials credentials = new PermissiveCredentials();

    /**
     * The pastry node that this application is running on
     */
    private PastryNode node; 

    /**
     * Hashtable of all the channels currently created on this node implicitly
     * or explicitly.
     */
    private Hashtable channels;

    /**
     * Set of ISplitStreamApps waiting for the SplitStream object to be ready,
     * which is ready when Scribe is ready
     */
    private Set m_apps = new HashSet();
    
    /**
     * Flag for whether this obj is ready
     */
    private boolean m_ready = false;


    /**
     * Flag for identifying whether the most recent drop
     * of a node is because local node dropped it, not because
     * that node sent unsubscription message.
     */
    private boolean localDrop = false;


    /**
     * The constructor for building the splitStream object
     *
     * @param node the pastry node that we will use
     * @param scribe the scribe instance to use
     *
     */
     public SplitStreamImpl(PastryNode node, IScribe scribe){
 	super(node);
        this.scribe = scribe;  
 	this.scribe.registerScribeObserver(this);
	this.scribe.registerApp(this);
        this.node = node;
	this.channels = new Hashtable();
   }

   /**
    * This method is used by a peer who wishes to distribute the content
    * using SplitStream. It creates a Channel Object consisting of numStripes
    * number of Stripes, one for each stripe's content. A Channel object is
    * responsible for implementing SplitStream functionality, like maintaining
    * multiple multicast trees, bandwidth management and discovering parents
    * having spare capacity. One Channel object should be created for each 
    * content distribution which wishes to use SplitStream. 
    * @param numStripes - number of parts into which the content will
    *        be striped, each part is a Stripe.
    * @return an instance of a Channel class. 
    */
   public Channel createChannel(int numStripes, String name){

	Channel channel = new Channel(numStripes, name, scribe, credentials, bandwidthManager, this);
	channels.put(channel.getChannelId(), channel);
	return (channel);

   }

   /**
    * This method is used by peers who wish to listen to content distributed 
    * by some other peer using SplitStream. It attaches the local node to the 
    * Channel which is being used by the source peer to distribute the content.
    * Essentially, this method finds out the different parameters of Channel
    * object which is created by the source, (the peer distributing the content)    
    *, and then creates a local Channel object with these parameters and
    * returns it.  
    * This is a non-blocking call so the returned Channel object may not be 
    * initialized with all the parameters, so applications should wait for 
    * channelIsReady() notification made by channels when they are ready. 
    * @param channelId - Identifier of channel to which local node wishes
    *  to attach to. 
    * @return  An instance of Channel object.
    */
   public Channel attachChannel(ChannelId channelId){
     Channel channel = (Channel) channels.get(channelId);
     //System.out.println("Attempting to attach to Channel " + channelId);
     if(channel == null){

     	channel = new Channel(channelId, scribe, credentials, bandwidthManager, this);
	channels.put(channelId, channel);
     }
	return channel;
   }

   /**
    * Gets the bandwidth manager associated with this splitstream object
    * 
    * @return BandwidthManager that is associated with this splitstream
    */ 
   public BandwidthManager getBandwidthManager(){
      return bandwidthManager;
   }

   /**
    * Sets the bandwidthManager for this splitstream
    *
    * @param bandwidthManager the new bandwidthManager
    */
   public void setBandwidthManager(BandwidthManager bandwidthManager){
       this.bandwidthManager = bandwidthManager;
   }

   /** - IScribeObserver Implementation -- */

   /**
    * The update method called when a new topic is created at this
    * node.  When a new topic is created we join it so we can receive
    * any more messages that come for it.
    *
    * @param topicId the new topic being created
    */
    public void update(Object topicId){
	if(scribe.join((NodeId) topicId, this, credentials)){
        }
        else
          System.out.println("ERROR: Could not join Channel being created");
   } 

   /** - IScribeApp Implementation -- */
   /**
    * The method called when a fault occurs. Currently not implemented.
    *
    * @param msg The message to be sent on the failure
    * @param faultyParent the node that caused the fault 
    */
    public void faultHandler(ScribeMessage msg, NodeHandle faultyParent){}
   public void forwardHandler(ScribeMessage msg){}
   public void receiveMessage(ScribeMessage msg){
     /* Check the type of message */
     /* then make call accordingly */
     //System.out.println("Recieved message");
   }
  
   /**
    * Upcall generated when the underlying scribe is ready
    */ 
   public void scribeIsReady(){
       //System.out.println("Scribe is Ready");
       m_ready = true;
       notifyApps();
   }

   /**
    * The upcall generated when a subscribe message is received
    * This currently handles the  implicit creation of channel objects
    * when they don't exist at this node
    *
    * @param topicId the topic being subscribed/dropped
    * @param child the child to be added/dropped
    * @param wasAdded whether the operation was a subscribe or an unsubscribe
    * @param data the data that was in the subscribe/unsubscribe message
    * 
    */
   public void subscribeHandler(NodeId topicId, 
                               NodeHandle child, 
                               boolean wasAdded,  
                               Serializable data){

       //System.out.println("Subscribe Handler at " + ((Scribe) scribe).getNodeId() + " for " + topicId + " from " + child.getNodeId());
       NodeId[] nodeData = (NodeId[]) data;
       
       
       if(nodeData!=null){
	   /* Clean This up */
	   StripeId[] stripeId = new StripeId[nodeData.length - 2];
	   ChannelId channelId = new ChannelId(nodeData[0]);
	   for(int i = 1; i < nodeData.length -1; i++){
	       stripeId[i-1] = new StripeId(nodeData[i]);
	   }
	   
	   
	   SpareCapacityId spareCapacityId = new SpareCapacityId(nodeData[nodeData.length -1]);
	   /* Clean This up */
	   Channel channel = (Channel)channels.get(channelId);
	   
	   if(channel == null){
	       channel = new Channel(channelId, stripeId, spareCapacityId, scribe, bandwidthManager, this);
	       channels.put(channelId, channel);
	   }
	   
	   /**
	    * Check if this subscription is for a unsubscribed-stripe of this channel!
	    * If so, then the splitstream object should take the responsibility
	    * of notifying the bandwidth manager about the bandwidth usage, and additional
	    * handling, like sending ControlPropogatePathMessage or Drop message if necessary.
	    */
	   if(!((NodeId)channelId).equals(topicId)){
	       //System.out.println("SPLITSTREAM :: Subscription is for stripe"+topicId+" at "+channel.getNodeId()+ " from "+child.getNodeId());
	       Vector subscribedStripes = channel.getSubscribedStripes();
	       if(!channel.stripeAlreadySubscribed((StripeId)topicId)){
		   Stripe stripe = channel.getStripe((StripeId)topicId);
		   if(wasAdded){
		       //System.out.println("SPLITSTREAM :: Used bw "+bandwidthManager.getUsedBandwidth(channel)+" max bw "+bandwidthManager.getMaxBandwidth(channel));
		       if(bandwidthManager.canTakeChild(channel)){
			   //System.out.println("SPLITSTREAM :: Subscriber can take child"+child.getNodeId()+" at "+channel.getNodeId());
			   channel.stripeSubscriberAdded();
			   Credentials credentials = new PermissiveCredentials();
			   Vector child_root_path = (Vector)stripe.getRootPath().clone();
			   child_root_path.add( ((Scribe)scribe).getLocalHandle() );
			   this.routeMsgDirect( child, new ControlPropogatePathMessage( this.getAddress(),
	   this.getNodeHandle(),
           topicId,
	   credentials, child_root_path, channel.getChannelId() ),
			   credentials, null );
		       }
		       else{
			   /* THIS IS WHERE THE DROP SHOULD OCCUR */
			   Credentials credentials = new PermissiveCredentials();
			   NodeHandle victimChild = null;
			   StripeId victimStripeId = null;
			   Vector ret;
			   
			   // Now, ask the bandwidth manager to free up some bandwidth, fill up
			   // victimChild and victimStripeId ..
			   // XXX - might want to change how freeBandwidth returns :-)
			   ret = bandwidthManager.freeBandwidth(channel);
			   victimChild = (NodeHandle)ret.elementAt(0);
			   victimStripeId = (StripeId)ret.elementAt(1);

			   Stripe victimStripe = channel.getStripe(victimStripeId);
			   Vector child_root_path = (Vector)stripe.getRootPath().clone();
			   child_root_path.add( ((Scribe)scribe).getLocalHandle() );

			   /**
			    * In all cases except the case that victimChild is the same as the recently added
			    * child and also for the same stripe, then we need to send the propogate path
			    * message to recently added child.
			    */
			   if(!(victimChild.getNodeId().equals(child.getNodeId()) &&
				topicId.equals((NodeId)victimStripeId))){
			       this.routeMsgDirect( child, new ControlPropogatePathMessage( this.getAddress(),
            this.getNodeHandle(),
	    topicId,
	    credentials,
	    child_root_path,
            channel.getChannelId() ),
						       credentials, null );
			       //System.out.println("Sending PROPOGATE message to"+child.getNodeId()+ " for stripe "+topicId);
			   }
			   
			   this.routeMsgDirect( victimChild, new ControlDropMessage( this.getAddress(),
     this.getNodeHandle(),
     victimStripeId,
     credentials,
     channel.getSpareCapacityId(), 
     channel.getChannelId(), 
     channel.getTimeoutLen() ),
     credentials, null );
			   //System.out.println("Sending DROP message to "+victimChild.getNodeId()+" for stripe"+victimStripeId+" at "+channel.getNodeId());
			   victimStripe.setLocalDrop(true);
			   scribe.removeChild(victimChild, (NodeId)victimStripeId);
		       }
		   }
		   else{
		       //System.out.println("SPLITSTREAM ::Subscriber was removed"+child.getNodeId()+" at "+channel.getNodeId());
		       if(!stripe.getLocalDrop()){
			   channel.stripeSubscriberRemoved();
		       }
		       else {
			   stripe.setLocalDrop(false);
		       }
		   }
	       }
	       
	   }
       }
   }
    
    /**
     * Returns the underlying scribe object.
     */
    public IScribe getScribe(){
	return scribe;
    }
    
    /**
     * registers an app to be notified when a fault occurs
     * that is so severe that the application can not handle it
     * by automatically repairing it. For example if there is
     * no spare capacity left in the system and node is looking
     * for a new parent
     * 
     * @param app the app to be registered
     */
    public void registerApp(ISplitStreamApp app){
	m_apps.add(app);
    } 
   
    /**
     * called when the apps registered with this splitstream
     * object must be notified of event that splitstream is ready.
     *
     */
    public void notifyApps(){
	Iterator it = m_apps.iterator();
	
	while(it.hasNext()){
	    ISplitStreamApp app = (ISplitStreamApp)it.next();
	    app.splitstreamIsReady();
	}
    }
   
    /**
     * Get PastryNode returns a pastry node
     * @returns PastryNode the Node
     */
    public PastryNode getPastryNode(){
      return thePastryNode;
    }

    /** -- Pastry Implementation -- **/
    
    /**
     * Returns the application address of this pastry application
     * @return Address the application's address
     */
    public Address getAddress(){
	return address;
    }
    
    /**
     * Gets the security credentials for this pastry application
     *
     * @return Credentials the credentials
     */
    public Credentials getCredentials(){
	return null;
    }

    /**
     * MessageForAppl takes a message in from pastry,
     * determines what type of message it is and then 
     * sends it to the appropriate sub routine to be handled
     */
    public void messageForAppl (Message msg){
	/**
         * This should be cleaned up, but since we are past the
         * Api freeze I don't want to change the class inheritance hierachy
         */
       if( (msg instanceof ControlAttachMessage) ){
	   ChannelId channelId = ((ControlAttachMessage) msg).getChannelId();
           Channel channel = (Channel) channels.get(channelId);
           channel.messageForChannel(msg);
       }
       else if (msg instanceof ControlFindParentResponseMessage){
	   ChannelId channelId = ((ControlFindParentResponseMessage) msg).getChannelId();
           Channel channel = (Channel) channels.get(channelId);
           channel.messageForChannel(msg);
       }
       else if (msg instanceof ControlDropMessage){
	   ChannelId channelId = ((ControlDropMessage) msg).getChannelId();
           Channel channel = (Channel) channels.get(channelId);
           channel.messageForChannel(msg);
       }
       else if(msg instanceof ControlFindParentMessage){
	   ChannelId channelId = ((ControlFindParentMessage) msg).getChannelId();
           Channel channel = (Channel) channels.get(channelId);
           channel.messageForChannel(msg);
       }
       else if(msg instanceof ControlPropogatePathMessage){
	   ChannelId channelId = ((ControlPropogatePathMessage) msg).getChannelId();
           Channel channel = (Channel) channels.get(channelId);
           channel.messageForChannel(msg);
       }
       else if(msg instanceof ControlTimeoutMessage){
	   ChannelId channelId = ((ControlTimeoutMessage) msg).getChannelId();
           Channel channel = (Channel) channels.get(channelId);
           channel.messageForChannel(msg);
       }
       else if(msg instanceof ControlAttachResponseMessage){
	   ChannelId channelId = ((ControlAttachResponseMessage) msg).getChannelId();
           Channel channel = (Channel) channels.get(channelId);
           channel.messageForChannel(msg);
       } 

           
    }
    /**
     * Upcall generate when a message is routed through this 
     * node. Again this is ugly but only way to get around
     * changing the  class heirarchy or adding an interface.
     *
     * @param msg the Message being routed
     * @return boolean if this method is succesful
     */
    public boolean enrouteMsg(Message msg){

      if( (msg instanceof ControlFindParentMessage) ){
         ChannelId channelId = ((ControlFindParentMessage)msg).getChannelId();
         Channel channel = (Channel) channels.get(channelId);
          
         if(channel == null)
            return true; 
         else
            return channel.enrouteChannel(msg);
      }
      else if(msg instanceof ControlAttachMessage){
         ChannelId channelId = ((ControlAttachMessage) msg).getChannelId();
         Channel channel = (Channel) channels.get(channelId);
          
         if(channel == null)
            return true; 
         else
            return channel.enrouteChannel(msg);
      }
      return true;
    }
    
} 

