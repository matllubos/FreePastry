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
 * @version $Id:
 * @author briang
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
	//System.out.println("Printing path for stripe "+recv_stripe.getStripeId() );
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
     * originator as a child and sends a FindParentResponse message back to the originator.  If
     * not, it initiates a DFS of the spare capacity tree.
     *
     * @param scribe The scribe group associated with this node
     * @param topic The scribe topic this message pertains to (should always be the spare capacity id)
     * @param channel The channel this message is relevant to
     * @param stripe The splitstream stripe this message pertains to
     * @return Should always return false, as message forwarding does not need to be done by
     * scribe from this point on
     */
    public boolean handleMessage( Scribe scribe, Topic topic, Channel channel, Stripe stripe )
    {
	recv_stripe = stripe;
        //System.out.println("Forwarding at " + scribe.getNodeId()+" for stipe "+stripe.getStripeId()+" from original source "+originalSource.getNodeId()+" topic "+topic);
        Credentials c = new PermissiveCredentials();
	Topic stripeTopic = scribe.getTopic(recv_stripe.getStripeId());

	if( ( topic == null) && ( send_to.size() == 0 ) ){
	    return true;
	}
	
	if( topic == null){
	    send_to.remove(0);
	    if ( send_to.size() > 0 ){
		channel.routeMsgDirect( (NodeHandle)send_to.get(0), this, c, null );
	    }
	    else{
		System.out.println("DFS FAILED :: No spare capacity");

		Vector sendPath = (Vector)recv_stripe.getRootPath().clone();
		sendPath.add(scribe.getLocalHandle());
		
		
		channel.routeMsgDirect( originalSource,
					new ControlFindParentResponseMessage( channel.getAddress(),
									      scribe.getNodeHandle(),
									      channel_id,
									      c,
									      new Boolean( false ), stripe_id,
									      sendPath),
					c,
					null );
	    }
	}

	else {
	    Vector children = scribe.getChildren(topic.getTopicId());
	    Vector toAdd = new Vector();
	    NodeHandle child;
	    
	    // Check if all my children are already visited or not,
	    // if visited, then i will check if i can take this child
	    // and if not, return to parent.
	    for(int i = 0; i < children.size(); i++){
		child = (NodeHandle)children.elementAt(i);
		if(!already_seen.contains(child) && !send_to.contains(child))
		    toAdd.add(child);
	    }
	    // my children have not been visited, so adding them
	    if(toAdd.size() > 0){
		if(!send_to.contains(scribe.getLocalHandle()))
		    send_to.add( 0, scribe.getLocalHandle());
		send_to.addAll(0, toAdd);
		channel.routeMsgDirect( (NodeHandle)send_to.get(0), this, c, null );
	    }
	    else {
		BandwidthManager bandwidthManager = channel.getBandwidthManager();
		
		/**
		 * Conditions to check
		 * 1) Should be part of stripe tree
		 * 2) Can take child
		 * 3) Is not generating cycles
		 * 4) Not attaching to itself.
		 */
		if((stripeTopic != null) && ( bandwidthManager.canTakeChild( channel ) ) &&
		   ( !isInRootPath( scribe, originalSource ) ) &&  ( !originalSource.equals( scribe.getLocalHandle()))){
			Vector subscribedStripes = channel.getSubscribedStripes();
			//System.out.println("NODE "+channel.getNodeId()+" TAKING ON CHILD "  + originalSource.getNodeId()+" for stripe " +recv_stripe.getStripeId());
			if(!subscribedStripes.contains(recv_stripe))
			    channel.stripeSubscriberAdded();
			scribe.addChild( originalSource, recv_stripe.getStripeId() );
			
			Vector sendPath = (Vector)recv_stripe.getRootPath().clone();
			sendPath.add(scribe.getLocalHandle());


			channel.routeMsgDirect( originalSource,
						new ControlFindParentResponseMessage( channel.getAddress(),
										      scribe.getNodeHandle(),
										      channel_id,
										      c,
										      new Boolean( true ), stripe_id,
										      sendPath),
						c,
						null );
			//System.out.println("Node "+scribe.getNodeId()+ " taking the child "+originalSource.getNodeId());
		}
		else {
		    // send to next node in  send_to list if this
		    // is not empty, else to parent
		    already_seen.add(scribe.getLocalHandle());
		    if(send_to.contains(scribe.getLocalHandle())){
			//System.out.println("Send to contains local node -- FINE");
			send_to.remove(0);
		    }
		    if(send_to.size() > 0)
			channel.routeMsgDirect( (NodeHandle)send_to.get(0), this, c, null );
		    else {
			if ( !scribe.isRoot( topic.getTopicId() ) )
			    channel.routeMsgDirect( scribe.getParent( topic.getTopicId() ), this, c, null );	  
			else {												
			    System.out.println("DFS FAILED :: No spare capacity");

			    Vector sendPath = (Vector)recv_stripe.getRootPath().clone();
			    sendPath.add(scribe.getLocalHandle());
			    
			    
			    channel.routeMsgDirect( originalSource,
						    new ControlFindParentResponseMessage( channel.getAddress(),
											  scribe.getNodeHandle(),
											  channel_id,
											  c,
											  new Boolean( false ), stripe_id,
											  sendPath),
						    c,
						    null );
			}
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

    public void printRootPath(Vector path){
	System.out.println("Printing Root Path");
	for(int i = 0; i < path.size(); i++)
	    System.out.println("<"+((NodeHandle)path.elementAt(i)).getNodeId()+" >");
    }
}







