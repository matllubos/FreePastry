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
    /**
     * Holds the list of NodeHandles to send to (used for DFS)
     */
    Vector send_to;

    /**
     * Holds the list of NodeHandles already examined (used for DFS)
     */
    Vector already_seen;

    /**
     * Stripe we are trying to attach to
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
     * @return Returns false unless the receiving node is not part of the spare capacity tree
     * scribe from this point on
     */
    public boolean handleMessage( Scribe scribe, Topic topic, Channel channel, Stripe stripe )
    {
	recv_stripe = stripe;
        //System.out.println("Forwarding at " + scribe.getNodeId()+" for stipe "+stripe.getStripeId()+" from original source "+originalSource.getNodeId()+" topic "+topic);
        Credentials c = new PermissiveCredentials();
	Topic stripeTopic = scribe.getTopic(recv_stripe.getStripeId());

        /*Node is not part of the spare capacity tree, and don't have a list 
          built up of anywhere else to forward this message to */
	if( ( topic == null) && ( send_to.size() == 0 ) ){
	    return true;
	}
	
        /*Receiving node is not part of the spare capacity tree*/
	if( topic == null){
	    send_to.remove(0);

            /*Something in the send to list, so we can forward the message along*/
	    if ( send_to.size() > 0 ){
		channel.getSplitStream().routeMsgDirect( (NodeHandle)send_to.get(0), this, c, null );
	    }
            /*Something broke along the way, and we can't find our way back to the
              spare capacity tree */
	    else{
		System.out.println("DFS FAILED :: No spare capacity");

		Vector sendPath = (Vector)recv_stripe.getRootPath().clone();
		sendPath.add(scribe.getLocalHandle());
		
		
		channel.getSplitStream().routeMsgDirect( originalSource,
					new ControlFindParentResponseMessage( channel.getSplitStream().getAddress(),
									      scribe.getNodeHandle(),
									      channel_id,
									      c,
									      new Boolean( false ), stripe_id,
									      sendPath),
					c,
					null );
	    }
	}

        /*Receiving node is part of the spare capacity tree*/
	else {
	    Vector children = scribe.getChildren(topic.getTopicId());
	    Vector toAdd = new Vector();
	    NodeHandle child;
	    
	    /*Check if all my children are already visited or not,
	      if visited, then I will check if I can take this child
	      and if not, return to parent.*/
	    for(int i = 0; i < children.size(); i++){
		child = (NodeHandle)children.elementAt(i);
		if(!already_seen.contains(child) && !send_to.contains(child))
		    toAdd.add(child);
	    }
	    /*My children have not been visited, so adding them*/
	    if(toAdd.size() > 0){
		if(!send_to.contains(scribe.getLocalHandle()))
		    send_to.add( 0, scribe.getLocalHandle());
		send_to.addAll(0, toAdd);
		channel.getSplitStream().routeMsgDirect( (NodeHandle)send_to.get(0), this, c, null );
	    }
            /*Visited children, so now time to check my own compatibility*/
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


			channel.getSplitStream().routeMsgDirect( originalSource,
						new ControlFindParentResponseMessage( channel.getSplitStream().getAddress(),
										      scribe.getNodeHandle(),
										      channel_id,
										      c,
										      new Boolean( true ), stripe_id,
										      sendPath),
						c,
						null );
			//System.out.println("Node "+scribe.getNodeId()+ " taking the child "+originalSource.getNodeId());
		}
                /* Have not visited all children, so want to send to them before checking
                   my own compatibility*/
		else {
		    /*Send to next node in send_to list if this
		      is not empty, else to parent*/
		    already_seen.add(scribe.getLocalHandle());
		    if(send_to.contains(scribe.getLocalHandle())){
			//System.out.println("Send to contains local node -- FINE");
			send_to.remove(0);
		    }
		    if(send_to.size() > 0)
			channel.getSplitStream().routeMsgDirect( (NodeHandle)send_to.get(0), this, c, null );
                    /*Sending to parent if not root*/
		    else {
			if ( !scribe.isRoot( topic.getTopicId() ) )
			    channel.getSplitStream().routeMsgDirect( scribe.getParent( topic.getTopicId() ), this, c, null );
                        /*This node is the root, which means no spare capacity exists (DFS failed)*/	  
			else {												
			    System.out.println("DFS FAILED :: No spare capacity");

			    Vector sendPath = (Vector)recv_stripe.getRootPath().clone();
			    sendPath.add(scribe.getLocalHandle());
			    
			    
			    channel.getSplitStream().routeMsgDirect( originalSource,
						    new ControlFindParentResponseMessage( channel.getSplitStream().getAddress(),
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







