package rice.splitstream.messaging;

import rice.splitstream.*;
import rice.scribe.messaging.*;
import rice.scribe.*;
import rice.pastry.*;
import rice.pastry.messaging.*;
import rice.pastry.security.*;
import java.util.Vector;
import java.util.Random;
import java.lang.Boolean;
import java.lang.Object;
import java.io.Serializable;

/**
 * This class represents the anycast message sent by a node upon receiving a
 * a failure from the ControlFindParentMessage. This is the last attempt 
 * made by a node to join the corresponding stripe. 
 *
 * This message implements the policy to look for those nodes in the 
 * stripe tree(for stripe the failed node is interested in) which have positive
 * outgoing capacity and have children for other stripes. The leaves in current
 * stripe tree are guaranteed to satisfy this condition. Now, these nodes are
 * asked to drop one of their child from other stripes (other than this) and 
 * take me as its child.
 *
 * @(#) ControlFinalFindParentMessage.java
 * @version $Id$
 * @author Atul Singh
 */
public class ControlFinalFindParentMessage extends MessageAnycast
{

    /**
     * SripeId of the stripe we are looking for.
     */
    StripeId stripe_id;

    /**
     * Corresponding stripe on the receiving Channel object
     */
    Stripe recv_stripe = null;

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
    SplitStreamImpl ss;

    /**
     * Credentials
     */
    Credentials cred;

    /**
     * Constructs a FinalFindParentMessage with the apropriate parameters
     * @param addr The address of the source of this message
     * @param source The NodeHandle of the source of this message
     * @param topicId The topic id for the stripe the node is looking for
     * @param c Credentials to send under
     * @param channel_id The channel we want to examine over
     */
    public ControlFinalFindParentMessage( Address addr, NodeHandle source, NodeId topicId, Credentials c,  ChannelId channel_id)
    {
	super( addr, source, topicId, c );
	this.channel_id = channel_id;
	this.originalSource = source;
	this.cred = c;
	this.stripe_id = (StripeId)topicId;
    }

    public StripeId getStripeId(){
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
	//System.out.println("Printing path for stripe "+recv_stripe.getStripeId()+ " at "+((Scribe)scribe).getNodeId() );
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
     * This method is called when the FinalFindParentMessage is received by a node.  The node should
     * determine whether it has children for other stripes and also if its outgoing capacity is positive
     * If yes, then a ControlFindParentResponse is sent to the originator, otherwise DFS is continued.
     * 
     * @param splitstream The SplitStream object associated with the node
     * @param scribe The scribe group associated with this node
     * @param channel The channel this message is relevant to
     * @param stripe The splitstream stripe this message pertains to
     * @return Returns true if local node was able to take on the child, else false
     */
    public boolean handleMessage(SplitStreamImpl splitstream, Scribe scribe, Channel channel, Stripe stripe )
    {
	recv_stripe = stripe;
	ss = splitstream;
	StripeId[] stripeIds = channel.getStripes();
	Random rng = new Random();
	int random = rng.nextInt(stripeIds.length);
	StripeId victimStripeId;
        System.out.println("Forwarding at " + scribe.getNodeId()+" for stipe "+stripe.getStripeId()+" from original source "+originalSource.getNodeId());
        Credentials c = new PermissiveCredentials();
	Topic stripeTopic = scribe.getTopic(recv_stripe.getStripeId());
	Vector children;
	BandwidthManager bandwidthManager = channel.getBandwidthManager();
	Vector rndStripe = new Vector();

	for(int k = 0; k < stripeIds.length; k++){
	    if(recv_stripe.getStripeId().equals(stripeIds[k]))
		continue;
	    victimStripeId = (StripeId) stripeIds[k];  
	    children = scribe.getChildren((NodeId)victimStripeId);
	    if(children != null && children.size() > 0)
		rndStripe.addElement(victimStripeId);
	}
	// If local nodes outgoing capacity is not positive or
	// it has no other stripe to drop some children from,
	// then it cant satisfy the request, hence should continue
	// looking 
	if(rndStripe.size() == 0 || ( bandwidthManager.getMaxBandwidth(channel) <= 0)) {
	    System.out.println("At "+scribe.getNodeId()+", rndStripe.size = "+rndStripe.size()+" maxBand = "+bandwidthManager.getMaxBandwidth(channel));
	    return true;
	}
	System.out.println("At "+scribe.getNodeId()+", rndStripe.size = "+rndStripe.size()+" maxBand = "+bandwidthManager.getMaxBandwidth(channel));
	
	/**
	 * Conditions to check
	 * 1) Should be part of stripe tree
	 * 2) Is not generating cycles
	 * 3) Not attaching to itself.
	 * 4) Is not itself looking for someone to attach to
	 */
	if(( stripeTopic != null) && 
	   ( !isInRootPath(scribe, originalSource)) &&  
	   ( !originalSource.equals( scribe.getLocalHandle())) &&
	   ( recv_stripe.getState() != Stripe.STRIPE_DROPPED)) {

	    Vector subscribedStripes = channel.getSubscribedStripes();



	    //if(!subscribedStripes.contains(recv_stripe))
	    //channel.stripeSubscriberAdded();

	    /*
	    if ( !scribe.addChild( originalSource, recv_stripe.getStripeId() ) )
		{
		    System.out.println( "Failure adding child "+originalSource.getNodeId()+" at "+
					scribe.getNodeHandle().getNodeId() );
					}*/
	    Vector sendPath = (Vector)recv_stripe.getRootPath().clone();
	    sendPath.add(scribe.getLocalHandle());

	    // select randomly one of the stripes out of many
	    // stripes, whose child we are going to drop
	    random = rng.nextInt(rndStripe.size());
	    victimStripeId = (StripeId)rndStripe.elementAt(random);
	    
	    // now, select one child randomly for the selected stripe
	    children = scribe.getChildren((NodeId)victimStripeId);
	    random = rng.nextInt(children.size());
	    NodeHandle victimChild = (NodeHandle)children.elementAt(random);

	    ss.routeMsgDirect( originalSource,
			       new ControlFindParentResponseMessage( ss.getAddress(),
								     scribe.getNodeHandle(),
								     channel_id,
								     c,
								     new Boolean( true ), 
								     stripe.getStripeId(),
								     sendPath),
			       c,
			       null );


	    ss.routeMsgDirect( originalSource,
			       new ControlPropogatePathMessage( ss.getAddress(),
								ss.getNodeHandle(),
								recv_stripe.getStripeId(),
								c,
								sendPath,
								channel_id ),
			       c,
			       null );
	    if(!scribe.removeChild(victimChild, (NodeId)victimStripeId))
		System.out.println("Failure in removeChild -- Child "+victimChild.getNodeId()+" at "+scribe.getNodeId());
	    if ( !scribe.addChild( originalSource, recv_stripe.getStripeId() ) )
		{
		    System.out.println( "Failure adding child "+originalSource.getNodeId()+" at "+
					scribe.getNodeHandle().getNodeId() );
		}
	    ss.routeMsgDirect( victimChild, 
			       new ControlDropMessage( ss.getAddress(),
						       ss.getNodeHandle(),
						       victimStripeId,
						       c,
						       channel.getSpareCapacityId(),
						       channel.getChannelId(),
						       channel.getTimeoutLen()),
			       c, 
			       null ); 
	    
	    //System.out.println("Node "+scribe.getNodeId()+ " taking the child "+originalSource.getNodeId());

	    System.out.println("NODE "+scribe.getNodeId()+" TAKING ON CHILD "  + originalSource.getNodeId()+" for stripe " +recv_stripe.getStripeId()+" and REMOVING CHILD "+victimChild.getNodeId()+" for stripe "+victimStripeId);
	    return false;
	}
	else {
	    if(isInRootPath( scribe, originalSource ) )
		System.out.println("ControlFinalFindParentMessage -- Could not satisfy the request at "+channel.getSplitStream().getNodeId()+" from "+originalSource.getNodeId()+" for "+stripe_id+" because loops are created");
	    if(recv_stripe.getState() == Stripe.STRIPE_DROPPED)
		System.out.println("ControlFinalFindParentMessage -- Could not satisfy the request at "+channel.getSplitStream().getNodeId()+" from "+originalSource.getNodeId()+" for "+stripe_id+" because local node is dropped for this stripe");
	    if(stripeTopic == null)
		System.out.println("ControlFinalFindParentMessage -- Could not satisfy the request at "+channel.getSplitStream().getNodeId()+" from "+originalSource.getNodeId()+" for "+stripe_id+" because local node is not subscribed to the stripe");

	    return true;
	}
    }

    public void faultHandler(){
	System.out.println("ControlFinalFindParentMessage -- DFS Failed. Noone could take me "+originalSource.getNodeId()+" as a child. - traversed "+alreadySeenSize());
	
	Vector sendPath = (Vector)recv_stripe.getRootPath().clone();
	sendPath.add(((Scribe)ss.getScribe()).getLocalHandle());

	ss.routeMsgDirect( originalSource,
			   new ControlFindParentResponseMessage( ss.getAddress(),
								 ((Scribe)ss.getScribe()).getNodeHandle(),
								 channel_id,
								 cred,
								 new Boolean( false ), 
								 recv_stripe.getStripeId(),
								 sendPath),
			   cred,
			   null );
    }


    public String toString()
    {
        return null;
    }

    public void printRootPath(Vector path){
	System.out.println("Printing Root Path");
	for(int i = 0; i < path.size(); i++)
	    System.out.println("<"+((NodeHandle)path.elementAt(i)).getNodeId()+" >");
    }
}







