package rice.splitstream.messaging;

import rice.splitstream.*;
import rice.scribe.messaging.*;
import rice.scribe.*;
import rice.pastry.*;
import rice.pastry.messaging.*;
import rice.pastry.security.*;
import java.util.Vector;
import java.lang.Boolean;
import java.io.Serializable;

/**
 * This class represents the anycast message sent by a node upon receiving a
 * drop notification from its former parent.  It is sent to the spare
 * capacity tree in an attempt to find a new parent.
 *
 * @(#) ControlFindParentMessage.java
 * @version $Id$
 * @author briang
 * @author Atul Singh
 */
public class ControlFindParentMessage extends MessageAnycast
{
    /**
     * Stripe we are trying to attach to
     */
    StripeId stripe_id;

    /**
     * Corresponding stripe on the receiving Channel object
     */
    transient Stripe recv_stripe = null;

    /**
     * Channel we are examining over
     */
    ChannelId channel_id;

    /**
     * The primal originator of this message
     */
    NodeHandle originalSource;
    
    /**
     * Splitstream object on local node.
     */
    transient SplitStreamImpl ss;


    transient Channel m_channel;
    /**
     * Credentials
     */
    Credentials cred;

    /**
     * Constructs a FindParentMessage with the apropriate parameters
     * @param addr The address of the source of this message
     * @param source The NodeHandle of the source of this message
     * @param topicId The topic id for the spare capacity tree
     * @param c Credentials to send under
     * @param stripe_id The stripe we want to attach to
     * @param channel_id The channel we want to examine over
     */
    public ControlFindParentMessage( Address addr, NodeHandle source, NodeId topicId, Credentials c, StripeId stripe_id, ChannelId channel_id)
    {
	super( addr, source, topicId, c );
	this.stripe_id = stripe_id;
	this.channel_id = channel_id;
	this.originalSource = source;
	this.cred = c;
    }

    public StripeId getStripeId()
    {
	return stripe_id;
    }

    public ChannelId getChannelId()
    {
        return channel_id;
    }

    /**
     * This method determines whether a given source node is in the path to root of this node for a
     * given stripe tree.
     * @param scribe The scribe object existing at the receiving Channel object
     * @param source Source node's handle
     */
    private boolean isInRootPath( IScribe scribe, NodeHandle source )
    {
	//System.out.println("DEBUG :: Printing path for stripe "+recv_stripe.getStripeId()+ " at "+((Scribe)scribe).getNodeId() );
	//printRootPath(recv_stripe.getRootPath());
        if ( recv_stripe != null )
	    {
		if ( recv_stripe.getRootPath() != null )
		    {
			return recv_stripe.getRootPath().contains( source );
		    }
		else
		    {
			//receiving node has been dropped; don't attach
			return true;
		    }
	    }
        else
	    {
		return false;
	    }
    }


    /**
     * This method is called when the FindParentMessage is received by a node.  The node should
     * determine whether it is able to handle an additional child.  If so, it adds the message
     * originator as a child and sends a FindParentResponse message back to the originator and 
     * returns true.  If not, it returns false which causes
     * furthur DFS to take place.
     *
     * @param scribe The scribe group associated with this node
     * @param channel The channel this message is relevant to
     * @param stripe The splitstream stripe this message pertains to
     * @return Returns true if local node was able to take on the child, else false
     */
    public boolean handleMessage(SplitStreamImpl splitstream, Scribe scribe, Channel channel, Stripe stripe )
    {
	recv_stripe = stripe;
	ss = splitstream;
	m_channel = channel;
        //System.out.println("DEBUG :: Forwarding at " + scribe.getNodeId()+" for stipe "+stripe.getStripeId()+" from original source "+originalSource.getNodeId()+" topic "+topic);
        Credentials c = new PermissiveCredentials();
	Topic stripeTopic = scribe.getTopic(recv_stripe.getStripeId());

	BandwidthManager bandwidthManager = channel.getBandwidthManager();
	
	/**
	 * Conditions to check
	 * 1) Should be part of stripe tree
	 * 2) Can take child
	 * 3) Is not generating cycles
	 * 4) Not attaching to itself.
	 */
	if((stripeTopic != null) && ( bandwidthManager.canTakeChild( channel ) ) &&
	   ( !isInRootPath( scribe, originalSource ) ) &&  
	   ( !originalSource.equals( scribe.getLocalHandle())) &&
	   recv_stripe.getState() != Stripe.STRIPE_DROPPED){
	   //recv_stripe.getState() == Stripe.STRIPE_SUBSCRIBED){
	    Vector subscribedStripes = channel.getSubscribedStripes();
	    System.out.println("DEBUG :: NODE "+scribe.getNodeId()+" TAKING ON CHILD "  + originalSource.getNodeId()+" for stripe " +recv_stripe.getStripeId());

	    Vector children = scribe.getChildren((NodeId)recv_stripe.getStripeId());
	    //if(!subscribedStripes.contains(recv_stripe))
	    //channel.stripeSubscriberAdded();
	   

	    Vector sendPath = (Vector)recv_stripe.getRootPath().clone();
	    sendPath.add(scribe.getLocalHandle());
	    
	    
	    ControlFindParentResponseMessage cfprmsg = new ControlFindParentResponseMessage( 
											    channel.getSplitStream().getAddress(),
											    scribe.getNodeHandle(),
											    channel_id,
											    c,
											    new Boolean( true ), stripe_id,
											    sendPath,
											    false);
	

	    ControlPropogatePathMessage cpgmsg = new ControlPropogatePathMessage( channel.getSplitStream().getAddress(),
										  channel.getSplitStream().getNodeHandle(),
										  stripe_id,
										  c,
										  sendPath,
										  channel_id );
	    
	    
	    AckData data = new AckData(cfprmsg,cpgmsg);
	    
	    if ( !scribe.addChild( originalSource, (NodeId)recv_stripe.getStripeId(), data ) )
		{
		    // We should free this bandwidth, since we already have this node as our child.
		    System.out.println( "DEBUG :: Failure adding child "+originalSource.getNodeId()+" at "+
					scribe.getNodeHandle().getNodeId()+" for topic "+recv_stripe.getStripeId()+ " in ControlFindParentMessage");
		    //channel.stripeSubscriberRemoved();
		}
	   
	    //System.out.println("DEBUG :: Node "+scribe.getNodeId()+ " taking the child "+originalSource.getNodeId());
	    return false;
	}
	else {
	    /*
	    if(!bandwidthManager.canTakeChild( channel ))
		System.out.println("DEBUG :: ControlFindParentMessage -- Could not satisfy the request at "+channel.getSplitStream().getNodeId()+" from "+originalSource.getNodeId()+" for "+stripe_id+" because no spare capacity");
	    if(isInRootPath( scribe, originalSource ) )
		System.out.println("DEBUG :: ControlFindParentMessage -- Could not satisfy the request at "+channel.getSplitStream().getNodeId()+" from "+originalSource.getNodeId()+" for "+stripe_id+" because loops are created");
	    if(recv_stripe.getState() == Stripe.STRIPE_DROPPED)
		System.out.println("DEBUG :: ControlFindParentMessage -- Could not satisfy the request at "+channel.getSplitStream().getNodeId()+" from "+originalSource.getNodeId()+" for "+stripe_id+" because local node is also dropped for this stripe");
	    if(stripeTopic == null)
		System.out.println("DEBUG ::ControlFindParentMessage -- Could not satisfy the request at "+channel.getSplitStream().getNodeId()+" from "+originalSource.getNodeId()+" for "+stripe_id+" because local node is not subscribed to the stripe");
	    */
	    return true;
	}
    }

    public void faultHandler(Scribe scribe){
	System.out.println("DEBUG ::ControlFindParentMessage -- DFS Failed. Noone could take me "+originalSource.getNodeId()+" at "+scribe.getNodeId()+" as a child. - traversed "+alreadySeenSize()+" for stripe "+getStripeId());


	scribe.routeMsgDirect( originalSource,
			   new ControlFindParentResponseMessage( SplitStreamAddress.instance(),
								 scribe.getNodeHandle(),
								 channel_id,
								 cred,
								 new Boolean( false ), stripe_id,
								 null,
								 true),
			   cred,
			   null );
    }

    public void printRootPath(Vector path){
	System.out.println("DEBUG ::Printing Root Path");
	for(int i = 0; i < path.size(); i++)
	    System.out.println("DEBUG ::<"+((NodeHandle)path.elementAt(i)).getNodeId()+" >");
    }
}







