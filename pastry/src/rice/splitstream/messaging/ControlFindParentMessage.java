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
 */
public class ControlFindParentMessage extends Message implements Serializable
{
    Vector send_to;
    Vector already_seen;
    StripeId stripe_id; // the stripe which we are looking for
    Stripe recv_stripe = null;
    ChannelId channel_id;
    NodeHandle originalSource ;
    final int DEFAULT_CHILDREN = 20;

    // topicId is the spare capacity tree Id.
    // stripeId is the stripe id for which local node is interested in .
    public ControlFindParentMessage( Address addr, NodeHandle source, NodeId topicId, Credentials c, StripeId stripe_id, ChannelId channel_id)
    {
	super( addr );
	send_to = new Vector();
	already_seen = new Vector();
	this.stripe_id = stripe_id;
	this.channel_id = channel_id;
	this.originalSource = source;
    }

    public StripeId getStripeId()
    {
	return stripe_id;
    }

    /**
     * This method determines whether a given source node is in the path to root of this node for a
     * given stripe tree.
     * @param splitStream This node
     * @param source Source node's handle
     * @param stripe_id Stripe ID for stripe tree to examine over
     */
    private boolean isInRootPath( IScribe scribe, NodeHandle source )
    {
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
     * This method returns the total number of children of this node, over all topics.
     */
    private int aggregateNumChildren( Scribe scribe )
    {
        Vector topics = scribe.getTopics();
        int total = 0;

        for (int i=0; i<topics.size(); i++)
	    {
		total += scribe.numChildren( ((Topic)topics.get(i)).getTopicId() );
	    }

        return total;
    }

    /**
     * This is the callback method for when this message should be forwarded to another
     * node in the spare capacity tree.  This node does not have any spare capacity
     * and is unable to take on the message originator as a child.
     * @param scribe The SplitStream application
     * @param topic The stripe that this message is relevant to
     */
    public boolean handleMessage( Scribe scribe, Topic topic, Channel channel, Stripe stripe )
    {
	recv_stripe = stripe;
        //System.out.println("Forwarding at " + scribe.getNodeId()+" for stipe "+stripe.getStripeId());
        Credentials c = new PermissiveCredentials();
	Topic stripeTopic = scribe.getTopic(recv_stripe.getStripeId());
        if ( stripeTopic == null )
	    {
		//System.out.println("Topic is null for stripe "+recv_stripe.getStripeId());
		if ( send_to.size() != 0 )
		    {
			already_seen.add( 0, send_to.remove(0) );
		    }
		else
		    {
			already_seen.add( 0, scribe.getNodeHandle() );
		    }
		Vector v = scribe.getChildren( topic.getTopicId() );
		//System.out.println( "Children of node "+ scribe.getNodeId() + " are " + v );
		if ( v != null )
		    {
			send_to.addAll( 0, scribe.getChildren( topic.getTopicId() ) );
		    }
		while ( ( send_to.size() > 0 ) &&
			( already_seen.contains( send_to.get(0) ) ) )
		    {
			send_to.remove( 0 );
		    }
		if ( send_to.size() > 0 )
		    {
			channel.routeMsgDirect( (NodeHandle)send_to.get(0), this, c, null );
		    }
		else
		    {
			if ( !scribe.isRoot( topic.getTopicId() ) )
			    {
				channel.routeMsgDirect( scribe.getParent( topic.getTopicId() ), this, c, null );
				//System.out.println( "Forwarding to parent at node "+scribe.getNodeId() );
			    }
			else
			    {
				System.out.println( "You're screwed, we're at the root" );
				channel.routeMsgDirect( originalSource,
							new ControlFindParentResponseMessage( channel.getAddress(),
											      scribe.getNodeHandle(),
											      channel_id,
											      c,
											      new Boolean( false ), stripe_id ),
							c,
							null );
			    }
		    }
	    }
	else
	    {
		if ( send_to.size() != 0 )
		    {
			already_seen.add( 0, send_to.remove(0) );
		    }
		else
		    {
			already_seen.add( 0, scribe.getNodeHandle() );
		    }
		BandwidthManager bandwidthManager = channel.getBandwidthManager();
		
		if ( ( bandwidthManager.canTakeChild( channel ) ) &&
		     ( !isInRootPath( scribe, originalSource ) ) &&
		     ( originalSource != scribe.getLocalHandle()) )
		    {
			//System.out.println("TAKING ON CHILD "  + originalSource.getNodeId());
			scribe.addChild( originalSource, recv_stripe.getStripeId() );
			//System.out.println("List of children for node "+scribe.getNodeId()+ " for topicid "+stripe_id+" recv_strpie.getStripe "+recv_stripe.getStripeId());
			//Vector vc = scribe.getChildren((NodeId)recv_stripe.getStripeId() );
			//for(int i = 0; i < vc.size(); i++)
			//   System.out.println("**"+((NodeHandle)vc.elementAt(i)).getNodeId()+" **");
			channel.stripeSubscriberAdded();
			channel.routeMsgDirect( originalSource,
						new ControlFindParentResponseMessage( channel.getAddress(),
										      scribe.getNodeHandle(),
										      channel_id,
										      c,
										      new Boolean( true ), stripe_id ),
						c,
						null );
			int default_children;

			if ( !bandwidthManager.canTakeChild( channel ) )
			    {
				//scribe.leave( topic.getTopicId(), null, c );
			    }
	   
			//System.out.println("Node "+scribe.getNodeId()+ " taking the child "+originalSource.getNodeId());

		    }
		else
		    {   if(topic == null) System.out.println("TOPIC IS NULL");
		    Vector v = scribe.getChildren( topic.getTopicId() );
		    //System.out.println( "Children of node "+ scribe.getNodeId() + " are " + v );
		    if ( v != null )
			{
			    send_to.addAll( 0, scribe.getChildren( topic.getTopicId() ) );
			}
		    while ( ( send_to.size() > 0 ) &&
			    ( already_seen.contains( send_to.get(0) ) ) )
			{
			    send_to.remove( 0 );
			}
		    if ( send_to.size() > 0 )
			{
			    channel.routeMsgDirect( (NodeHandle)send_to.get(0), this, c, null );
			}
		    else
			{
			    if ( !scribe.isRoot( topic.getTopicId() ) )
				{
				    channel.routeMsgDirect( scribe.getParent( topic.getTopicId() ), this, c, null );
				    //System.out.println( "Forwarding to parent at node "+scribe.getNodeId() );
				}
			    else
				{
				    System.out.println( "You're screwed, we're at the root" );
				    channel.routeMsgDirect( originalSource,
							    new ControlFindParentResponseMessage( channel.getAddress(),
												  scribe.getNodeHandle(),
												  channel_id,
												  c,
												  new Boolean( false ), stripe_id ),
							    c,
							    null );
				}
			}
		    }
	    }
	return false;
    }

    public String toString()
    {
        return null;
    }
}






