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
 * The channel controls all the meta  data associated with a group of 
 * stripes. It contains the stripes themselves plus any sparecapcity groups
 * associated with the group of stripes.  It also manages the amount of
 * bandwidth that is used by this collection of stripes.  A Channel is 
 * created by giving it a name which is then hashed to come up with a 
 * channelId which uniquely identifies this channel. If other nodes want
 * to join the channel they attach to it. ( Join the scribe group ) 
 *
 * This is the channel object that represents a group of stripes in
 * SplitStream.
 *
 * @version $Id$
 * @author Ansley Post
 * @author Atul Singh
 */
public class Channel implements IScribeApp {

    /**
     * ChannelId for this channel 
     */
    private ChannelId channelId = null;

    /**
     * The Node id the spare capacity tree is rooted at.
     */
    private SpareCapacityId spareCapacityId = null;

    /**
     * The hashtable mapping StripeId -> Stripes 
     */
    private Hashtable stripeIdTable = new Hashtable();

    /**
     * A vector containing all of the subscribed stripes for this channel
     */
    private Vector subscribedStripes = new Vector();

    /**
     * The primary stripe for this node.
     */
    private Stripe primaryStripe = null;

    /**
     * The number of stripes contained in this channel.
     */
    private int numStripes = 0;

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
  
    /**
     * The splitStreamImpl object associated with this node, this is
     * needed to have access to the pastry messages
     */
    private SplitStreamImpl splitStream = null;


    /**
     * If this channel is ready for use or not 
     */
    private boolean isReady = false;

  
    /**
     * The bandwidth manager for this channel, responsible for 
     * keeping track of the number of children, and then deciding
     * when to take on children.
     */
    private BandwidthManager bandwidthManager = null;

    /**
     * Set of ISplitStreamApps waiting for this channel to be ready.
     */
    private Set m_apps = new HashSet();

    /**
     * Number of timeouts allowable before generating an upcall;
     * -1 indicates an infinite number of retries
     */
    private int max_timeouts = 3;

    /**
     * Length of time to wait for a response before timing out
     */
    private long timeoutLen = 5000;

    /**
     * Should we ignore any attach-related timeout messages received
     */
    private boolean ignore_timeout = true;

    /**
     * Constructor to create a new channel from scratch
     * 
     * @param numStripes the number of stripes this channel will contain
     * @param name the Name to be associated with this channel
     * @param scribe the scribe service this Channel will utilize
     * @param cred the credentials associated with this user
     * @param bandwidthManager the object that controls bw utilization
     * @param splitStream the splitStream instance for this node 
     *
     */
    public Channel(int numStripes, String name, IScribe scribe, Credentials cred,                  BandwidthManager bandwidthManager, SplitStreamImpl splitStream){

        /* This method should probably broken down into smaller sub methods */
        
	this.scribe = scribe;
	this.bandwidthManager = bandwidthManager;
	this.numStripes = numStripes;
        this.splitStream = splitStream;
	
	/* register this channel with the bandwidthManager, scribe */
	this.bandwidthManager.registerChannel(this);
	scribe.registerApp(this);
 
        /* create the topic */
        NodeId topicId = ((Scribe)this.scribe).generateTopicId(name);
        if(scribe.create(topicId, cred)){
	    System.out.println("Channel Topic Created");
	    this.channelId = new ChannelId(topicId);
        }
        else{
           System.out.println("ERROR: Channel Topic Creation Failed");
        }

        /* Spare Capacity Id currently fixed to aid debugging */
        //topicId = random.generateNodeId();
        topicId = scribe.generateTopicId(name + "SPARECAPACITY");
        if(scribe.create(topicId, cred)){
	    this.spareCapacityId = new SpareCapacityId(topicId);
        }
        else
           System.out.println("ERROR: Spare Capacity Topic Creation Failed");

	System.out.println("Spare Capacity Id "+topicId);
        /* Stripe Id base also fixed to aid debugging */
        //NodeId baseId = random.generateNodeId();
	System.out.println("Creating "+numStripes+" number of stripes ");
        NodeId baseId = scribe.generateTopicId(name + "STRIPES");
	for(int i = 0; i < this.numStripes; i++){
	    StripeId stripeId = new StripeId(baseId.getAlternateId(numStripes, 4, i)); 
	    Stripe stripe = new Stripe(stripeId, this, scribe,cred,true);
	    stripeIdTable.put(stripeId, stripe);
	    System.out.println("Created stripe "+stripeId);
            /* This is the code to select the primary stripe, check it */ 
	    if(stripeId.getDigit(getSplitStream().getRoutingTable().numRows() -1, 4) 
		== getSplitStream().getNodeId().getDigit(getSplitStream().getRoutingTable().numRows() -1,4))
		primaryStripe = stripe; 
	    

	    //primaryStripe = stripe;	
	}
        sendSubscribeMessage();
	if(scribe.join(spareCapacityId, this, cred)){
	}
        else{
          System.out.println("ERROR: Could not join Spare Capacity Tree");
        }
	if(!isReady){
	    isReady = true;
	    System.out.println("Channel is ready at "+((Scribe)scribe).getNodeId()+" at the creator");
	    notifyApps();
	}
   }  

   /**
    * Helper method for above constructor 
    */
   private void sendSubscribeMessage(){   
	//primaryStripe.joinStripe(observer);
   	NodeId[] subInfo = new NodeId[this.numStripes + 2]; 
	subInfo[0] = channelId;
	for(int i = 1; i < subInfo.length -1; i++){
	    subInfo[i] = getStripes()[i - 1];
	}
	subInfo[subInfo.length-1] = spareCapacityId;
	if(scribe.join(channelId, this, cred, subInfo)){
	}		
        else{
          System.out.println("ERROR: Could not join Channel Tree");
        }
    }

    /**
     * Constructor to create a Channel when a channelID is known
     * 
     * @param channelId the id for the newly created channel 
     * @param scribe the scribe service this Channel will utilize
     * @param cred the credentials associated with this user
     * @param bandwidthManager the object that controls bw utilization
     * @param splitStream the splitStream instance for this node 
     */ 
    public Channel(ChannelId channelId, IScribe scribe, Credentials cred, 
		   BandwidthManager bandwidthManager, 
                   SplitStreamImpl splitStream){
	
	this.channelId = channelId;
	this.bandwidthManager = bandwidthManager;
	this.scribe = scribe;
        this.splitStream = splitStream;
	this.bandwidthManager.registerChannel(this);
        scribe.registerApp(this);
	ControlAttachMessage attachMessage = 
               new ControlAttachMessage(((Scribe)this.scribe).getAddress(),
                                        this.getSplitStream().getNodeHandle(),
                                        this.channelId,
					cred
                                       );
        //System.out.println("Sending Anycast Message from " + getNodeId());
        System.out.println("Sending ControlAttach Message");
        //this.getSplitStream().routeMsg(channelId, attachMessage, cred, null );
	scribe.anycast(channelId, attachMessage, cred);
        ignore_timeout = false;
        ControlTimeoutMessage timeoutMessage = new ControlTimeoutMessage( getSplitStream().getAddress(), 0, channelId, cred, channelId );
	this.splitStream.getPastryNode().scheduleMsg( timeoutMessage, timeoutLen );

    }


    void initialize(StripeId[] stripeIds, SpareCapacityId spareCapacityId){
	// initialize the channel metadata if its not ready yet
	if(!isReady){
	    this.spareCapacityId = spareCapacityId;
	    NodeId[] subInfo = new NodeId[stripeIds.length + 2];
	    subInfo[0] = this.channelId;
	    for(int i = 0; i < stripeIds.length; i++){
		if(stripeIdTable == null){
		    System.out.println("Debug :: StripeIdTable is null in Channel.java");
		}
		Stripe stripe = new Stripe( stripeIds[i], this, scribe, cred, false);
		stripeIdTable.put(stripeIds[i], stripe);
		subInfo[i+1] = (NodeId)stripeIds[i];
		/**
		 * Select the primary stripe
		 */
		if( stripeIds[i].getDigit(getSplitStream().getRoutingTable().numRows() -1, 4) 
		    == getSplitStream().getNodeId().getDigit(getSplitStream().getRoutingTable().numRows() -1,4) )
		    primaryStripe = stripe;
	    }
	    subInfo[stripeIds.length + 1] = spareCapacityId;

	    if(scribe.join(this.channelId, this, cred, subInfo)){
	    }
	    else{
		System.out.println("ERROR: Could not join Channel");
	    }

	    if(scribe.join(spareCapacityId, this, cred)){
	    }
	    else{
		System.out.println("ERROR: Could not join Spare Capacity Tree");
	    }
	    
	    isReady = true;
	    System.out.println("Channel is ready at "+((Scribe)scribe).getNodeId()+" from initialize messages");
	    notifyApps();
	}
    }

    /**
     * Constructor for channels made from subscribe methods 
     *
     * @param channelId the id for the newly created channel 
     * @param spareCapacityId the id for the spare capacity tree for channel
     * @param stripeIds the array of stripeIds associated with the channel
     * @param bandwidthManager the object that controls bw utilization
     * @param splitStream the splitStream instance for this node 
     */ 
    public Channel(ChannelId channelId, StripeId[] stripeIds, SpareCapacityId 
		   spareCapacityId, IScribe scribe,
                   BandwidthManager bandwidthManager,
                   SplitStreamImpl splitStream ){

	this.channelId = channelId;
	this.spareCapacityId = spareCapacityId;
        this.splitStream = splitStream;
	NodeId[] subInfo = new NodeId[stripeIds.length + 2];

	subInfo[0] = channelId;
	System.out.println("Channel -- created from subscribe method, stripeids.length ="+stripeIds.length);
	for(int i = 0 ; i < stripeIds.length ; i++){
	    if(stripeIdTable == null) {System.out.println("NULL");}
            Stripe stripe = new Stripe( stripeIds[i], this, scribe, cred, false);
	    stripeIdTable.put(stripeIds[i], stripe);
	    subInfo[i+1] = stripeIds[i];
	    /* Subscribe to a primary stripe */
            /* This is the code to select the primary stripe, check it */ 
	    if( stripeIds[i].getDigit(getSplitStream().getRoutingTable().numRows() -1, 4) 
	        == getSplitStream().getNodeId().getDigit(getSplitStream().getRoutingTable().numRows() -1,4) )
	        primaryStripe = stripe; 
	
	}
	subInfo[stripeIds.length + 1] = spareCapacityId;
	this.numStripes = stripeIds.length;
	this.scribe = scribe;
	this.bandwidthManager = bandwidthManager;
	this.bandwidthManager.registerChannel(this);
	if(scribe.join(channelId, this, cred, subInfo)){
	}
        else{
          System.out.println("ERROR: Could not join Channel Tree");
        }	
	if(scribe.join(spareCapacityId, this, cred)){
	}	
        else{	
          System.out.println("ERROR: Could not join Spare Capacity Tree");
        }
	if(!isReady){
	    isReady = true;
	    notifyApps();
	    System.out.println("Channel is ready at "+((Scribe)scribe).getNodeId()+" from subscribe methods");
	    //System.out.println("A Channel Object is being created (In Path) at " + getNodeId());
	}
    }
 
    /**
     * Channel Object is responsible for managing local node's usage of
     * outgoing bandwidth and incoming bandwidth, which is indicated by number
     * of stripes the local node has tuned to.
     *
     * What happens if outChannel < bandwidthUsed?
     *
     * @param int The outgoing bandwidth 
     * The incoming bandwidth is assumed from the outgoing.
     *
     */
    public void configureChannel(int outChannel){
	bandwidthManager.adjustBandwidth(this, outChannel); 
    }

    /**
     * Gets the bandwidth manager for this channel.
     * @return BandwidthManager the BandwidthManager for this channel
     */
    public BandwidthManager getBandwidthManager(){
	return bandwidthManager;
    }

    /**
     * Gets the splitStream instance that this channel was created from
     * @return SplitStreamImpl the SplitStreamObject for this channel
     */
     public SplitStreamImpl getSplitStream(){
        return splitStream;
     }
 

    /**
     * Gets the channelId for this channel
     * @return ChannelId the channelId for this channel
     */
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
     * setStripes allows the setting of stripe data
     * 
     * @param s the data to be used
     */
    public void setStripes( StripeId[] s){
        for(int i = 0; i < s.length; i++){
	  Stripe stripe = new Stripe(s[i], this, scribe,cred,true);
	  stripeIdTable.put(s[i], stripe);
	    if(s[i].getDigit(getSplitStream().getRoutingTable().numRows() -1, 4) 
		== getSplitStream().getNodeId().getDigit(getSplitStream().getRoutingTable().numRows() -1,4))
		primaryStripe = stripe; 
        }
    }

    /**
     * setSpareCapacityId allows the setting of spareCapacityId 
     * 
     * @param spareCapacityId the data to be used
     */
    public void setSpareCapacityId(SpareCapacityId spareCapacityId){
      this.spareCapacityId = spareCapacityId;
    }

    /**
     * At any moment a node is subscribed to at least 1 but possibly
     * more stripes. They will always be subscribed to their primary
     * Stripe.
     * @return Vector the Stripes this node is subscribed to.
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
    public boolean isReady(){
       return isReady;
    }

    /**
     * Returns the maximum timeouts allowable for this channel instance
     * @return int The currently set max timeouts
     */
    public int getTimeouts()
    {
        return max_timeouts;
    }

    /**
     * Returns this channel object's maximum time that can elapse before
     * a timeout is declared
     * @return long The currently set timeout length
     */
    public long getTimeoutLen()
    {
        return timeoutLen;
    }

    /**
     * Sets the channel-specific timeout message ignoring
     * @param ignore The new state of the channel-specific ignore
     */
    public void setIgnoreTimeout( boolean ignore )
    {
        ignore_timeout = ignore;
    }

    /**
     * Should channel-relevant timeout messages be ignored?
     * @return The current state of channel's ignoring timeouts
     */
    public boolean getIgnoreTimeout()
    {
        return ignore_timeout;
    }

    /**
     * Returns a random stripe from this channel that the user is not
     * currently subscribed to
     * @param observer the Object that is going to observe the stripe
     * @return Stripe A random stripe
     */
    public Stripe joinAdditionalStripe(Observer observer ){
        /**
         * Check to see if we have subscribed to all the stripes
         * in the channel. If we have return null.
         */
	if(getNumSubscribedStripes() == getNumStripes())
	    return null;

	boolean found= false;
	Stripe toReturn = null;
        for(int i = 0 ; i < getStripes().length && !found; i ++){
	    Stripe stripe = (Stripe) stripeIdTable.get(getStripes()[i]);

	    //System.out.println("Stripe "+stripe.getStripeId()+" state "+stripe.getState());
            /**
             * Limits the search to only nonsubscribed stripes
             */
    	    //if(stripe.getState() != Stripe.STRIPE_SUBSCRIBED){
	    if(stripe.getState() == Stripe.STRIPE_UNSUBSCRIBED){
		toReturn = stripe;
		toReturn.joinStripe();	
		toReturn.addObserver(observer);
		subscribedStripes.addElement(toReturn);
		found = true;
	    }
	    else {
		// we are subscribed to this stripe,
		// so should check if channel knows about
		// this, if not add this to subscribedStripe
		if(!subscribedStripes.contains(stripe)){
		    toReturn = stripe;
		    toReturn.joinStripe();
		    subscribedStripes.addElement(stripe);
		    toReturn.addObserver(observer);
		    found = true;
		}
	    }
	}
	return toReturn;
				
    }

    /**
     * Join a specific Stripe of this channel
     * @param stripeID The stripe to subscribe to
     * @param observer the Object that is going to observe the stripe
     * @return the Stripe joined
     */ 
    public Stripe joinStripe(StripeId stripeId, Observer observer){

	Object tableEntry = stripeIdTable.get(stripeId);
	Stripe stripe = null; 
	stripe = (Stripe) tableEntry;

        /** 
         * Checks to see if we are already subscribed to this
         * stripe, if we are just return.
         */ 
	if(subscribedStripes.contains(stripe))
	    return stripe;

	stripe.joinStripe();	
	stripe.addObserver(observer);
	subscribedStripes.addElement(stripe);
	return(stripe);
    }

    /**
     * Leave a random stripe
     * @return the stripeID left 
     */
    public StripeId leaveStripe(){

        /**
         * If there are no subscriped stripes just return
         * null
         */
	if(subscribedStripes.size() == 0)
	    return null;
       Stripe stripe = (Stripe) subscribedStripes.firstElement();
       subscribedStripes.removeElement(stripe);
       stripe.leaveStripe(); 
       return stripe.getStripeId();
    }
 
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
   
    /**
     * Gets the spareCapacityId for this channel
     * @return SpareCapacityId the spareCapacityId for this Channel
     */
    public SpareCapacityId getSpareCapacityId(){
	return spareCapacityId;
    }
    
    /**
     * Call used for stripes to notify channel they have taken on a subscriber.
     */
    public void stripeSubscriberRemoved(){

	bandwidthManager.additionalBandwidthFreed(this);
	int currentUsage = bandwidthManager.getUsedBandwidth(this);
	int maxAllowed = bandwidthManager.getMaxBandwidth(this);

        /**
         * Checks to see if when we remove this stripe if we can rejoin
         * the spare capcity tree
         */	
	if((currentUsage + 1) == maxAllowed){
	    System.out.println("Node " + getSplitStream().getNodeId() + " Joining Spare Capacity Tree Again");
	    if(scribe.join(getSpareCapacityId(), this, cred)){
            }
            else{
              System.out.println("ERROR: Failed to join Spare Capacity Tree");
            }
	}
    }

     /**
     * Call used for stripes to notify channel they have taken on a subscriber.
     */
    public void stripeSubscriberAdded(){
	bandwidthManager.additionalBandwidthUsed(this);

        /**
         * If after taking on this child in can't take on another
         * then we leave the spare capacity tree
         */
	if(!bandwidthManager.canTakeChild(this)){
	    System.out.println("Node " + getSplitStream().getNodeId() + " Leaving Spare Capacity Tree");
	    scribe.leave(getSpareCapacityId(), this, cred);
	}
    }


    /** -- Scribe Implementation -- **/

    /** 
     * The method called when a fault occurs
     *
     */
    public void faultHandler(ScribeMessage msg, NodeHandle faultyParent){
	/**
         * If this fault is occuring in the channel tree and not  
         * a spare capacity tree, we must get the meta data for the
         * channel
         */
        if(!msg.getTopicId().equals((NodeId)getSpareCapacityId())){
	    NodeId[] data = getChannelMetaData();
	    msg.setData(data);
	}
    }
    
    /**
     * The method called when a Scribe Message is forwarded
     * Currently not Implemented
     */
    public void forwardHandler(ScribeMessage msg){}
    
    /**
     * up-call invoked by Scribe when an anycast message is being handled.
     */
    public boolean anycastHandler(ScribeMessage msg){
	boolean result = true;
	if(msg instanceof ControlFindParentMessage){
	    ControlFindParentMessage pmsg = (ControlFindParentMessage)msg;
	    result= pmsg.handleMessage(getSplitStream(), getScribe(), this, this.getStripe(pmsg.getStripeId()) );
	}
	if(msg instanceof ControlAttachMessage){
	    ControlAttachMessage amsg = (ControlAttachMessage)msg;
	    result = amsg.handleMessage(this, getScribe());
	}
	if(msg instanceof ControlFinalFindParentMessage){
	    ControlFinalFindParentMessage pmsg = (ControlFinalFindParentMessage)msg;
	    result= pmsg.handleMessage(getSplitStream(), getScribe(), this, this.getStripe(pmsg.getStripeId()) );
	}

	return result;
    }

    /**
     * Handles Scribe Messages that the application recieves.
     * Right now the channel can receive two types of messages
     * Attach Requests
     *
     */
    public void receiveMessage(ScribeMessage msg){
	/* Check the type of message */
	/* then make call accordingly */

	Serializable obj = (Serializable)msg.getData();
	/* Check the type of message */
	/* then make call accordingly */
	System.out.println("Channel - Recieved message through anycast");
	if(obj instanceof ControlAttachMessage){
	    System.out.println("Recieved ControlAttachMessage message through Anycast");
	    ControlAttachMessage amsg = (ControlAttachMessage)obj;
	    ChannelId channelId = amsg.getChannelId();
	    handleAttachMessage(amsg);
	}
	/*
	else if(obj instanceof ControlFindParentMessage){
	    ControlFindParentMessage parentMessage = (ControlFindParentMessage)obj;
	    Stripe stripe = null;
	    
	    if(stripeIdTable.get(parentMessage.getStripeId()) instanceof Stripe){
		stripe = (Stripe) stripeIdTable.get(parentMessage.getStripeId());	
	    }
	    
	    parentMessage.handleMessage((Scribe ) scribe,
					       ((Scribe)scribe).getTopic(getSpareCapacityId()), this, stripe); 
	    
					       }*/
	/*
	if(msg.getTopicId().equals(channelId)){
	    //handleChannelMessage(msg);
	}
	else if(msg.getTopicId().equals(spareCapacityId)){
	    //handleSpareCapacityMessage(msg);
	    }*/
	
	else{
	    System.out.println(msg.getTopicId());
	    System.out.println("Unknown Scribe Message");
        }
    }

    /**
     * Upcall generated when the underlying scribe layer is ready
     * Currently not implemented
     */
    public void scribeIsReady(){
    }
    
    /**
     * Upcall generated when a new subscribe is added 
     * We don't care how many people subscribe to a channel, so not used
     */
    public void subscribeHandler(NodeId topicId, 
				 NodeHandle child, boolean wasAdded, Serializable data){}


    /**
     * MessageForChannel takes a message in from pastry,
     * determines what type of message it is and then 
     * sends it to the appropriate sub routine to be handled
     */
    public void messageForChannel (Message msg){
	if(msg instanceof ControlAttachResponseMessage){
	    handleControlAttachResponseMessage(msg);
	}
	else if(msg instanceof ControlFindParentResponseMessage){
	    handleControlFindParentResponseMessage(msg);
	}
	else if(msg instanceof ControlDropMessage){
	    handleControlDropMessage(msg);
	}
	else if(msg instanceof ControlFindParentMessage){
	    System.out.println("Channel - Control Find Parent Msg should not come here ");
	    handleControlFindParentMessage(msg); 
	}
	else if ( msg instanceof ControlPropogatePathMessage )
	{
	   handleControlPropogatePathMessage( msg );
	}
        else if ( msg instanceof ControlTimeoutMessage )
        {
            handleControlTimeoutMessage( msg );
        }
	/*
        else if( msg instanceof ControlAttachMessage){
            System.out.println("Delivering Attach Message");
            handleAttachMessage(msg); 
        }
	*/
	else{
	    System.out.println("Unknown Pastry Message Type");
	}
    }

    /**
     * Upcall generated when a message is routed through this 
     * node.
     *
     * @param msg the Message being routed
     * @return boolean if this method is succesful
     */
    public boolean enrouteChannel(Message msg){
	if(msg instanceof ControlFindParentMessage){
	    return handleControlFindParentMessage(msg); 
	}
	/*
        else if(msg instanceof ControlAttachMessage){
            System.out.println("Enrouting Attach Message");
            return handleAttachMessage(msg);
        }
	*/
	return true;
    }

    /**
     * Handles the ControlAttachResponseMessage that is received
     * through pastry.  It takes the information and uses it to
     * fill in the data structures for this channel such as StripeIds etc.
     *
     * @param msg the msg to handle
     */
    private void handleControlAttachResponseMessage(Message msg){
        
	if(!isReady){
	    NodeId[] subInfo = (NodeId[]) ((ControlAttachResponseMessage) msg).getContent();	
	    System.out.println(((Scribe)scribe).getNodeId() + " recieved a response to anycast "+subInfo.length);
	    channelId = new ChannelId(subInfo[0]);
	    spareCapacityId = new SpareCapacityId(subInfo[subInfo.length-1]);
	    
	    /* Fill in all instance variable for channel */
	    for(int i = 1 ; i < subInfo.length-1 ; i++){
		this.numStripes = subInfo.length -2 ;
		StripeId stripeId = new StripeId(subInfo[i]);
		Stripe stripe = new Stripe( stripeId, this, scribe, cred, false);
		stripeIdTable.put(stripeId, stripe);
		/* Subscribe to a primary stripe */
		/* This is the code to select the primary stripe, check it */ 
		if( stripeId.getDigit(getSplitStream().getRoutingTable().numRows() -1, 4) 
		    == getSplitStream().getNodeId().getDigit(getSplitStream().getRoutingTable().numRows() -1,4) )
		    primaryStripe = stripe;
		
	    }
	    if(scribe.join(channelId, this, cred, subInfo)){
	    }
	    else{
		System.out.println("ERROR: Could not join channel tree");
	    }
	    if(scribe.join(spareCapacityId, this, cred)){
	    }
	    else{	
		System.out.println("ERROR: Could not spare capacity tree");
	    }

	    isReady = true;
	    ignore_timeout = true;
	    System.out.println("Channel is ready at "+((Scribe)scribe).getNodeId()+" after receiving AttachResponse");
	    notifyApps();
	}
    }

    /** 
     * Handles the ControlFindParentResponseMessage received through pastry
     * 
     * @param msg the ControlFindParentMessage to handle
     */ 
    private void handleControlFindParentResponseMessage(Message msg){
	ControlFindParentResponseMessage prmessage = 
            (ControlFindParentResponseMessage) msg;
	Stripe stripe = (Stripe) stripeIdTable.get(prmessage.getStripeId());

	if(stripe != null){
	    Vector path = prmessage.getPath();
	    //System.out.println("Setting root path in controlFindparentResponse msg - setting it to "+prmessage.getSource().getNodeId()+" at "+getSplitStream().getNodeId() + " for stripe "+stripe.getStripeId());
	    stripe.setRootPath( path );
        }
        //stripe.setIgnoreTimeout( true );
	prmessage.handleMessage((Scribe) scribe, 
          ((Scribe) scribe).getTopic(prmessage.getStripeId()), stripe);
    }

    /**
     * Handles the ControlDropMessage received through pastry
     *
     * @param msg the ControlDropMessage to handle
     */
    private void handleControlDropMessage(Message msg){
	
        ControlDropMessage dropMessage = (ControlDropMessage) msg;
	Stripe stripe = (Stripe) stripeIdTable.get(dropMessage.getStripeId());

	if(stripe != null){
          stripe.dropped();
	  //System.out.println("Setting root path in controlDropMessage msg - making it empty at"+getNodeId()+" for stripe "+stripe.getStripeId());
          stripe.setRootPath( null );
        }
	//System.out.println("Node "+getNodeId()+" received DROP message"+" for stripe "+dropMessage.getStripeId());
	dropMessage.handleDeliverMessage((Scribe)scribe, 
           ((Scribe) scribe).getTopic(dropMessage.getStripeId()), getSplitStream().getPastryNode(), this);
    }


    /**
     * Handles a scribe message that is destined for the channel
     *
     * Assumes a ControlAttachMessage because that is the only message
     * that can currently go to the channel obj
     *
     * @param msg the ScribeMessage for this channel
     */
    private boolean handleAttachMessage(ControlAttachMessage attachMsg){
	System.out.println("Channel - Should not receive Attach Message through Pastry");
	return true;
        //ControlAttachMessage attachMsg = (ControlAttachMessage) msg.getData();
	//return attachMsg.handleMessage(this, scribe, attachMsg.getSource());
    }

    /**
     * Handles the message that is received through the spare capacity
     * tree.
     *
     * Assumes this is a SpareCapacityMessage as that is the only kind of
     * message that can be routed to the spareCapacityIds currently
     *
     * @param msg the Scribe Message recieved
     */
    private void handleSpareCapacityMessage(ScribeMessage msg){

	//System.out.println("SpareCapacity Message from " + msg.getSource().getNodeId() + " at " + getNodeId());
	/*
	Stripe stripe = null;
	ControlFindParentMessage parentMessage = 
          (ControlFindParentMessage) msg.getData();

	if(stripeIdTable.get(parentMessage.getStripeId()) instanceof Stripe){
	  stripe = (Stripe) stripeIdTable.get(parentMessage.getStripeId());	
	}

	parentMessage.handleMessage((Scribe) scribe, 
          ((Scribe)scribe).getTopic(getSpareCapacityId()), this, stripe);
	*/
    }
    
    /**
     *
     * Handles the ControlFindParentMessage through Pastry
     *
     * @param msg the message to handle
     */
    private boolean handleControlFindParentMessage(Message msg){
	return true;
	/*
	Stripe stripe = null;
	ControlFindParentMessage parentMessage = (ControlFindParentMessage) msg;

	if(stripeIdTable.get(parentMessage.getStripeId()) instanceof Stripe){
	   stripe = (Stripe) stripeIdTable.get(parentMessage.getStripeId());	
	}

	return parentMessage.handleMessage((Scribe ) scribe,
          ((Scribe)scribe).getTopic(getSpareCapacityId()), this, stripe); 
	*/
    }

    /**
     * Handles the ControlPropagatePathMessage used to propagate
     * data and detect cycles
     *
     * @param msg the message to be handled
     */  
    private void handleControlPropogatePathMessage( Message msg )
    {
	ControlPropogatePathMessage ppMessage = 
          (ControlPropogatePathMessage)msg;
	Stripe stripe = (Stripe)stripeIdTable.get( ppMessage.getStripeId() );
	if ( stripe != null ) {
          ppMessage.handleMessage( (Scribe)scribe, this, stripe );
	}
    }

    /**
     * Handles the ControlTimeoutMessage used to regulate timeouts on certain
     * messages (currently, Attach and FindParent)
     *
     * @param msg The message to be handled
     */
    private void handleControlTimeoutMessage( Message msg )
    {
	if( !ignore_timeout)
	    System.out.println( "Received a scheduled timeout message. Ignoring? "+ignore_timeout );
        ControlTimeoutMessage timeoutMessage = (ControlTimeoutMessage)msg;
        timeoutMessage.handleMessage( this, getSplitStream().getPastryNode(), (Scribe)this.scribe );
    }
 
    /**
     * This returns a string representation of the channel.
     * @return String String representation of the object.
     */
    public String toString(){
	String toReturn = "Channel: " + getChannelId() + "\n";
	toReturn = toReturn + "Stripes: \n";
	for(int i = 0; i < numStripes; i++){
	    toReturn = toReturn + "\t" + getStripes()[i] + "\n";
	}
	toReturn = toReturn + "Spare Capacity Id: " + getSpareCapacityId();
	return(toReturn);	
    }

    /**
     * Registers the application for an upcall
     * 
     * @param The application to be added
     */
    public void registerApp(ISplitStreamApp app){
	m_apps.add(app);
	if(isReady)
	    app.channelIsReady(getChannelId());
    }
   
    /**
     * Called to notify apps when an event occurs
     */
    public void notifyApps(){
	Iterator it = m_apps.iterator();

	while(it.hasNext()){
	    ISplitStreamApp app = (ISplitStreamApp)it.next();
	    app.channelIsReady(getChannelId());
	}
    }

    /**
     * Returns the meta data associated with the channel,
     * e.g. channelId, the number of stripes, the stripeIds and the 
     * spare capacity ids.
     *
     * @return meta-data associated with this channel
     */
    public NodeId[] getChannelMetaData(){
	NodeId[] subInfo = new NodeId[this.numStripes + 2]; 
	subInfo[0] = channelId;
	for(int i = 1; i < subInfo.length -1; i++){
	    subInfo[i] = getStripes()[i - 1];
	}
	subInfo[subInfo.length-1] = spareCapacityId;

	return subInfo;
    }

    /**
     * Returns the Stripe object associated with this stripeId
     *
     * @return the Stripe with StripeId equal to paramater if it exists
     */
    public Stripe getStripe(StripeId stripeId){
	return (Stripe)(stripeIdTable.get(stripeId));
    }

    /**
     * Checks to see if a given stripe has already been subscribed to.
     *
     * @return boolean whether we have subscribed or not
     */
    public boolean stripeAlreadySubscribed(StripeId stripeId){
	for(int i = 0; i < subscribedStripes.size(); i++){
	    Stripe stripe = (Stripe)subscribedStripes.elementAt(i);
	    if(stripe.getStripeId().equals(stripeId))
		return true;
	}
	return false;
    }


    /**
     * Return the underlying scribe object.
     *
     * @return Underlying scribe object
     */
    public Scribe getScribe(){
	return (Scribe)scribe;
    }
}

