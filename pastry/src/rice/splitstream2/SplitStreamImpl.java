package rice.splitstream2;

import java.io.*;
import java.util.*;

import rice.scribe.*;
import rice.scribe.messaging.*;

import rice.pastry.*;
import rice.pastry.client.*;
import rice.pastry.messaging.*;
import rice.pastry.security.*;

import rice.splitstream2.messaging.*;

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
	Channel channel = new Channel(numStripes, name, new BandwidthManager(), this);
	channels.put(channel.getChannelId(), channel);
	return null;
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
    public Channel attachChannel(String name){
        Channel channel = (Channel) channels.get(scribe.generateTopicId(name));
	if(channel == null){
	    channel = new Channel(DEFAULT_STRIPES, name, new BandwidthManager(), this);
	    channels.put(channel.getChannelId(), channel);
	}
	return channel;
    }


    protected NodeId createId(String s) {
       return(createId(scribe.generateTopicId(s)));
    }

    protected NodeId createId( NodeId id){
       boolean result = scribe.create(id, ((Scribe) scribe).getCredentials());
       if(result)
           return id;
       else
           return null;
    }

    protected NodeId[] createIds(String s, int num){
          NodeId[] toReturn = new NodeId[num];
          for(int i = 0 ; i < num; i++){
            /* put in some code to put in prefix differing ids */
            //createId(...)
          } 
          return toReturn;
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
	    System.out.println("DEBUG :: ERROR: Could not join Channel being created");
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
	//System.out.println("DEBUG :: Recieved message");
    }
    // Default implementation of anycastHandler.
    public boolean anycastHandler(ScribeMessage msg){
       return false;
    }
  
    /**
    * Upcall generated when the underlying scribe is ready
    */ 
    public void scribeIsReady(){
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
       return false;
    }
    
    /** 
     * Upcall by scribe to let this application know that 
     * it is the new root.
     */
    public void isNewRoot(NodeId topicId){
    }

    /**
     * Upcall by scribe to let this application know about
     * local node's new parent in the topic tree
     */
    public void newParent(NodeId topicId, NodeHandle newParent, Serializable data){
    }


    private static int DEFAULT_STRIPES = 16;
} 


