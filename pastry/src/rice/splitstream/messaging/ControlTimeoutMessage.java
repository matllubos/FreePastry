package rice.splitstream.messaging;

import rice.splitstream.*;
import rice.scribe.*;
import rice.scribe.messaging.*;
import rice.pastry.*;
import rice.pastry.messaging.*;
import rice.pastry.security.*;
import java.io.Serializable;

/**
 * This class represents a timeout event that could possibly
 * occur while waiting for delivery of a FindParent or Attach
 * message.  It should be scheduled for timed delivery to the
 * appropriate Channel or Stripe.
 *
 * @author Brian Greinke
 */
public class ControlTimeoutMessage extends Message implements Serializable
{
    /**
     * Constants representing the two possible types of messages associated
     * with a timeout
     */
    private static int ATTACH      = 1;
    private static int FIND_PARENT = 2;

    /**
     * The number of times the sent message this is related to has failed
     * to elicit a response.
     */
    private int num_fails;

    /**
     * The type of message associated with this timeout message
     */
    private int msg_type;

    /**
     * The destination of the associated message
     */
    private NodeId dest;

    /**
     * Credentials used for sending the associated message
     */
    private Credentials c;

    /**
     * The relevant stripe id of the associated message
     */
    private StripeId stripe_id;

    /**
     * The relevant channel id of the associated message
     */
    private ChannelId channel_id;

    /**
     * This is the constructor called when the timeout message is being created on behalf of
     * an Attach message.
     *
     * @param addr The address of the message source
     * @param num_fails The number of times the bound message has timed out thus far
     * @param dest The destination of the bound message
     * @param c The sending credentials of the bound message
     */
    public ControlTimeoutMessage( Address addr, int num_fails, NodeId dest, Credentials c )
    {
        super( addr );
        this.num_fails = num_fails;
	this.msg_type = ATTACH;
	this.dest = dest;
	this.c = c;
    }

    /**
     * This is the constructor called when the timeout message is being created on behalf of
     * a FindParent message.
     *
     * @param addr The address of the message source
     * @param num_fails The number of times the bound message has timed out thus far
     * @param dest The destination of the bound message
     * @param c The sending credentials of the bound message
     * @param stripe_id The stripe id the bound message pertains to
     * @param channel_id The channel id the bound message pertains to
     */
    public ControlTimeoutMessage( Address addr, int num_fails, NodeId dest, Credentials c, 
                                  StripeId stripe_id, ChannelId channel_id )
    {
        super( addr );
        this.num_fails = num_fails;
	this.msg_type = FIND_PARENT;
	this.dest = dest;
	this.c = c;
        this.stripe_id = stripe_id;
        this.channel_id = channel_id;
    }

    /**
     * This handler is called when the message has been scheduled for delivery
     * to a channel.  It determines whether the number of timeouts has exceeded
     * the allowable number of retries (specified in the calling channel).
     *
     * @param channel The calling channel
     */
    public void handleMessage( Channel channel, PastryNode thePastryNode, Scribe scribe )
    {
        if ( num_fails > channel.getTimeouts() )
        {
            /* Exceeded allowable number of retries; generate app-level upcall(?) */
        }
        else
        {
            if ( msg_type == ATTACH )
            {
        	ControlAttachMessage attachMessage = new ControlAttachMessage();
                scribe.anycast( dest, attachMessage, c );

                ControlTimeoutMessage timeoutMessage = new ControlTimeoutMessage( channel.getAddress(),
                                                                                  num_fails+1,
                                                                                  dest,
                                                                                  c );
	        thePastryNode.scheduleMsg( timeoutMessage, channel.getTimeoutLen() );
                
            }
            else if ( msg_type == FIND_PARENT )
            {
                ControlFindParentMessage msg = new ControlFindParentMessage( SplitStreamAddress.instance(), 
                                                                             scribe.getLocalHandle(),
                                                                             dest,
                                                                             c,
                                                                             stripe_id, channel_id );
                scribe.anycast( dest, msg, c ); 
                ControlTimeoutMessage timeoutMessage = new ControlTimeoutMessage( SplitStreamAddress.instance(),
                                                                                  num_fails+1,
                                                                                  dest,
                                                                                  c, stripe_id, channel_id );
                thePastryNode.scheduleMsg( timeoutMessage, channel.getTimeoutLen() );
           }
       }
    }	
}