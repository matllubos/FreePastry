package rice.splitstream2.messaging;

import rice.splitstream2.*;
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
       return false;
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
     return false;
    }


    public String toString()
    {
        return null;
    }

    public void printRootPath(Vector path){
	System.out.println("DEBUG :: Printing Root Path");
	for(int i = 0; i < path.size(); i++)
	    System.out.println("DEBUG :: <"+((NodeHandle)path.elementAt(i)).getNodeId()+" >");
    }
}







