package rice.splitstream2.messaging;

import rice.splitstream2.*;
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
      return false;
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
      return false;
    }
}







