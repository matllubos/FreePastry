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
 * Add paragraph here for class information 
 *
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
     * If this channel is ready for use or not 
     */
    private boolean isReady = false;

    /**
     * The pastry address of this application
     */
    private static Address address = SplitStreamAddress.instance();

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
     * Number of timeouts allowable before generating an upcall
     */
    private int max_timeouts = 5;

    /**
     * Length of time to wait for a response before timing out
     */
    private long timeoutLen = 5000;

    /**
     * Should we ignore any timeout messages received
     * <CLEAN THIS UP>
     */
    private boolean timeoutIgnore = true;

    /**
     * Constructor to create a new channel from scratch
     * 
     * @param numStripes the number of stripes this channel will contain
     * @param name the Name to be associated with this channel
     * @param scribe the scribe service this Channel will utilize
     * @param cred the credentials associated with this user
     * @param bandwidthManager the object that controls bw utilization
     * @param node the pastry node associated with this local channel
     *
     */
    public Channel(int numStripes, String name, IScribe scribe, Credentials cred,                  BandwidthManager bandwidthManager, PastryNode node){

        /* This method should probably broken down into smaller sub methods */
 	super(node);	
        
	this.scribe = scribe;
	this.bandwidthManager = bandwidthManager;
	this.numStripes = numStripes;
	
	/* register this channel with the bandwidthManager, scribe */
	this.bandwidthManager.registerChannel(this);
	scribe.registerApp(this);
 
        /* create the topic */
        NodeId topicId = this.scribe.generateTopicId(name);
        if(scribe.create(topicId, cred)){
	    System.out.println("Channel Topic Created");
	    this.channelId = new ChannelId(topicId);
        }

        /* Spare Capacity Id currently fixed to aid debugging */
        //topicId = random.generateNodeId();
        topicId = scribe.generateTopicId(name + "SPARECAPACITY");
        if(scribe.create(topicId, cred)){
	    this.spareCapacityId = new SpareCapacityId(topicId);
        }

        /* Stripe Id base also fixed to aid debugging */
        //NodeId baseId = random.generateNodeId();
        NodeId baseId = scribe.generateTopicId(name + "STRIPES");
	for(int i = 0; i < this.numStripes; i++){
	    StripeId stripeId = new StripeId(baseId.getAlternateId(numStripes, 4, i)); 
	    Stripe stripe = new Stripe(stripeId, this, scribe,cred,true);
	    stripeIdTable.put(stripeId, stripe);

            /* This is the code to select the primary stripe, check it */ 
	    /*	if(stripeId.getDigit(getRoutingTable().numRows() -1, 4) 
		== getNodeId().getDigit(getRoutingTable().numRows() -1,4))
		primaryStripe = stripe; 
	    */

	    primaryStripe = stripe;	
	}
        sendSubscribeMessage();
	if(scribe.join(spareCapacityId, this, cred)){
	    System.out.println("Creator Joined Spare Capacity Group" + getNodeId());
	}		
   	isReady = true;
	notifyApps();
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
	    System.out.println("Creator Joined Group" + getNodeId());
	}		
    }

    /**
     * Constructor to create a Channel when a channelID is known
     * 
     * @param channelId the id for the newly created channel 
     * @param scribe the scribe service this Channel will utilize
     * @param cred the credentials associated with this user
     * @param bandwidthManager the object that controls bw utilization
     * @param node the pastry node associated with this local channel
     */ 
    public Channel(ChannelId channelId, IScribe scribe, Credentials cred, 
		   BandwidthManager bandwidthManager, PastryNode node){
	
 	super(node);	
	this.channelId = channelId;
	this.bandwidthManager = bandwidthManager;
	this.scribe = scribe;
	this.bandwidthManager.registerChannel(this);
        scribe.registerApp(this);
	ControlAttachMessage attachMessage = new ControlAttachMessage();
        //System.out.println("Sending Anycast Message from " + getNodeId());
        scribe.anycast(channelId, attachMessage, cred );
        timeoutIgnore = false;
        ControlTimeoutMessage timeoutMessage = new ControlTimeoutMessage( this.getAddress(),
                                                                          0,
                                                                          channelId,
                                                                          cred );
	this.thePastryNode.scheduleMsg( timeoutMessage, timeoutLen );

    }

    /**
     * Constructor for channels made from subscribe methods 
     *
     * @param channelId the id for the newly created channel 
     * @param spareCapacityId the id for the spare capacity tree for channel
     * @param stripeIds the array of stripeIds associated with the channel
     * @param bandwidthManager the object that controls bw utilization
     * @param node the pastry node associated with this local channel
     */ 
    public Channel(ChannelId channelId, StripeId[] stripeIds, SpareCapacityId 
		   spareCapacityId, IScribe scribe, BandwidthManager bandwidthManager, PastryNode node){

 	super(node);
	this.channelId = channelId;
	this.spareCapacityId = spareCapacityId;
	

	for(int i = 0 ; i < stripeIds.length ; i++){
	    if(stripeIdTable == null) {System.out.println("NULL");}
	    stripeIdTable.put(stripeIds[i],
			      new Stripe( stripeIds[i], this, scribe, cred, false));
		
	}

	this.numStripes = stripeIds.length;
	this.scribe = scribe;
	this.bandwidthManager = bandwidthManager;
	this.bandwidthManager.registerChannel(this);
	if(scribe.join(channelId, this, cred)){
	}
	
	if(scribe.join(spareCapacityId, this, cred)){
	}		
	/* Subscribe to a primary stripe */
	isReady = true;
	notifyApps();
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
     * At any moment a node is subscribed to at least 1 but possibly
     * more stripes. They will always be subscribed to thier primary
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
     * Returns a random stripe from this channel that the user is not
     * currently subscribed to
     * @param observer the Object that is going to observe the stripe
     * @return Stripe A random stripe
     */
    public Stripe joinAdditionalStripe(Observer observer ){
	if(getNumSubscribedStripes() == getNumStripes())
	    return null;

	boolean found= false;
	Stripe toReturn = null;
        for(int i = 0 ; i < getStripes().length && !found; i ++){
	    Stripe stripe = (Stripe) stripeIdTable.get(getStripes()[i]);

	    if(stripe.getState() != Stripe.STRIPE_SUBSCRIBED){
		toReturn = stripe;
		toReturn.joinStripe();	
		toReturn.addObserver(observer);
		subscribedStripes.addElement(toReturn);
		found = true;
	    }
	}
	return toReturn;
				
    }

    /**
     * Join a specific Stripe of this channel
     * @param stripeID The stripe to subscribe to
     * @param observer the Object that is going to observe the stripe
     * @return boolean Success of the join operation
     */ 
    public Stripe joinStripe(StripeId stripeId, Observer observer){
	Object tableEntry = stripeIdTable.get(stripeId);
	Stripe stripe = null; 
	stripe = (Stripe) tableEntry;
	//if(subscribedStripes.contains(stripe))
	//  return stripe;

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
    public void stripeSubscriberAdded(){

	bandwidthManager.additionalBandwidthUsed(this);
	if(!bandwidthManager.canTakeChild(this)){
	    System.out.println("Node " + getNodeId() + " Leaving Spare Capacity Tree");
	    scribe.leave(getSpareCapacityId(), this, cred);
	}
    }

    /** -- Scribe Implementation -- **/
    /** 
     * The method called when a fault occurs
     * Currently not implemented
     *
     */
    public void faultHandler(ScribeMessage msg, NodeHandle faultyParent){
       /* Implement me */
    }
    
    /**
     * The method called when a Scribe Message is forwarded
     * Currently not Implemented
     */
    public void forwardHandler(ScribeMessage msg){}

    /**
     * Handles Scribe Messages that the application recieves.
     * Right now the channel can receive two types of messages
     * 1. Attach Requests
     * 2. Spare Capactiy Requests
     */
    public void receiveMessage(ScribeMessage msg){
	/* Check the type of message */
	/* then make call accordingly */
	if(msg.getTopicId().equals(channelId)){
	    handleChannelMessage(msg);
	}
	else if(msg.getTopicId().equals(spareCapacityId)){
	    handleSpareCapacityMessage(msg);
	}
	else{
	    System.out.println(msg.getTopicId());
	    System.out.println("Unknown Scribe Message");
        }
    }

    /**
     * Upcall generated when the underlying scribe layer is ready
     * Currently not implmented
     */
    public void scribeIsReady(){
    }
    
    /**
     * Upcall generated when a new subscribe is added 
     * We don't care how many people subscribe to a channel, so not used
     */
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
	else{
	    System.out.println("Unknown Pastry Message Type");
	}
    }
  
    public boolean enrouteMessage(Message msg){
	if(msg instanceof ControlFindParentMessage){
	    return handleControlFindParentMessage(msg); 
	}
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
        //System.out.println(getNodeId() + " recieved a response to anycast ");
	NodeId[] subInfo = (NodeId[]) ((ControlAttachResponseMessage) msg).getContent();	
	channelId = new ChannelId(subInfo[0]);
	spareCapacityId = new SpareCapacityId(subInfo[subInfo.length-1]);
	/* Fill in all instance variable for channel */
	for(int i = 1 ; i < subInfo.length-1 ; i++){
	    this.numStripes = subInfo.length -2 ;
	    StripeId stripeId = new StripeId(subInfo[i]);
	    stripeIdTable.put(stripeId, 
			      new Stripe( stripeId, this, scribe, cred, false));
	}
        if(scribe.join(channelId, this, cred, subInfo)){
	}
	if(scribe.join(spareCapacityId, this, cred)){
	}	
	isReady = true;
        timeoutIgnore = true;
	notifyApps();
    }

    /** 
     * Handles the ControlFindParentResponseMessage recieved through pastry
     * 
     * @param msg the ControlFindParentMessage to handle
     */ 
    private void handleControlFindParentResponseMessage(Message msg){

	ControlFindParentResponseMessage prmessage = 
            (ControlFindParentResponseMessage) msg;

	prmessage.handleMessage((Scribe) scribe, 
          ((Scribe) scribe).getTopic(prmessage.getStripeId()));
    }

    /**
     * Handles the ControlDropMessage recieved through pastry
     *
     * @param msg the ControlDropMessage to handle
     */
    private void handleControlDropMessage(Message msg){
	
        ControlDropMessage dropMessage = (ControlDropMessage) msg;
	Stripe stripe = (Stripe) stripeIdTable.get(dropMessage.getStripeId());

	if(stripe != null){
          stripe.dropped();
          stripe.setRootPath( null );
        }

	dropMessage.handleDeliverMessage((Scribe)scribe, 
           ((Scribe) scribe).getTopic(dropMessage.getStripeId()));
    }


    /**
     * Handles a scribe message that is destined for the channel
     *
     * Assumes a ControlAttachMessage because that is the only message
     * that can currently go to the channel obj
     *
     * @param msg the ScribeMessage for this channel
     */
    private void handleChannelMessage(ScribeMessage msg){

	ControlAttachMessage attachMessage =
           (ControlAttachMessage) msg.getData();

	attachMessage.handleMessage(this, scribe, msg.getSource());
    }

    /**
     * Handles the message that is recieved through the spare capacity
     * tree.
     *
     * Assumes this is a SpareCapacityMessage as that is the only kind of
     * message that can be routed to the spareCapacityIds currently
     *
     * @param msg the Scribe Message recieved
     */
    private void handleSpareCapacityMessage(ScribeMessage msg){

	//System.out.println("SpareCapacity Message from " + msg.getSource().getNodeId() + " at " + getNodeId());

	Stripe stripe = null;
	ControlFindParentMessage parentMessage = 
          (ControlFindParentMessage) msg.getData();

	if(stripeIdTable.get(parentMessage.getStripeId()) instanceof Stripe){
	  stripe = (Stripe) stripeIdTable.get(parentMessage.getStripeId());	
	}

	parentMessage.handleMessage((Scribe) scribe, 
          ((Scribe)scribe).getTopic(getSpareCapacityId()), this, stripe);

    }
    
    /**
     *
     * Handles the ControlFindParentMessage through pastry
     *
     * @param msg the message to handle
     */
    private boolean handleControlFindParentMessage(Message msg){

	Stripe stripe = null;
	ControlFindParentMessage parentMessage = (ControlFindParentMessage) msg;

	if(stripeIdTable.get(parentMessage.getStripeId()) instanceof Stripe){
	   stripe = (Stripe) stripeIdTable.get(parentMessage.getStripeId());	
	}

	return parentMessage.handleMessage((Scribe ) scribe,
          ((Scribe)scribe).getTopic(getSpareCapacityId()), this, stripe); 

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
        ControlTimeoutMessage timeoutMessage = (ControlTimeoutMessage)msg;
        if ( !timeoutIgnore )
        {
            timeoutMessage.handleMessage( this, this.thePastryNode, (Scribe)this.scribe );
        }
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
     * the spare capacity ids.
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

    public Stripe getStripe(StripeId stripeId){
	return (Stripe)(stripeIdTable.get(stripeId));
    }

    public boolean stripeAlreadySubscribed(StripeId stripeId){
	for(int i = 0; i < subscribedStripes.size(); i++){
	    Stripe stripe = (Stripe)subscribedStripes.elementAt(i);
	    if(stripe.getStripeId().equals(stripeId))
		return true;
	}
	return false;
    }
}
