package rice.splitstream.messaging;

import rice.splitstream.*;
import rice.scribe.*;
import rice.scribe.messaging.*;
import rice.pastry.*;
import rice.pastry.messaging.*;
import rice.pastry.security.*;
import java.io.Serializable;

import java.util.Random;
/**
 * This class represents a timeout event that could possibly
 * occur while waiting for delivery of a FindParent or Attach
 * message.  It should be scheduled for timed delivery to the
 * appropriate Channel or Stripe.
 *
 * @(#) ControlTimeoutMessage.java
 * @version $Id$
 * @author briang
 * @author Atul Singh
 */
public class ControlTimeoutMessage extends Message implements Serializable
{
    /**
     * Constants representing the three possible types of messages associated
     * with a timeout
     */
    private static int ATTACH      = 1;
    private static int FIND_PARENT = 2;
    private static int FINAL_FIND_PARENT = 3;
    private static Random  random = new Random();
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
     * @param addr The address of the message destination application
     * @param num_fails The number of times the bound message has timed out thus far
     * @param dest The destination of the bound message
     * @param c The sending credentials of the bound message
     * @param channel_id The channel id the bound message pertains to
     */
    public ControlTimeoutMessage( Address addr, int num_fails, NodeId dest, Credentials c, ChannelId channel_id )
    {
        super( addr );
        this.num_fails = num_fails;
	this.msg_type = ATTACH;
	this.dest = dest;
	this.c = c;
        this.channel_id = channel_id;
    }

    /**
     * This is the constructor called when the timeout message is being created on behalf of
     * a FindParent message.
     *
     * @param addr The address of the message destination application
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
     * This is the constructor called when the timeout message is being created on behalf of
     * a FinalFindParent message.
     *
     * @param addr The address of the message destination application
     * @param num_fails The number of times the bound message has timed out thus far
     * @param c The sending credentials of the bound message
     * @param stripe_id The stripe id the bound message pertains to
     * @param channel_id The channel id the bound message pertains to
     */
    public ControlTimeoutMessage( Address addr, int num_fails, Credentials c, 
                                  StripeId stripe_id, ChannelId channel_id )
    {
        super( addr );
        this.num_fails = num_fails;
	this.msg_type = FINAL_FIND_PARENT;
	this.c = c;
        this.stripe_id = stripe_id;
        this.channel_id = channel_id;
    }

    public ChannelId getChannelId()
    {
        return channel_id;
    }

    /**
     * This handler is called when the message has been scheduled for delivery
     * to a channel.  It determines whether the number of timeouts has exceeded
     * the allowable number of retries (specified in the calling channel).
     *
     * @param channel The calling channel
     * @param thePastryNode Pastry node to schedule another timeout for delivery to
     * @param scribe Scribe application that exists at the destination channel
     */
    public void handleMessage( Channel channel, PastryNode thePastryNode, Scribe scribe )
    {

        boolean ignore;
        if ( msg_type == ATTACH )
        {
            ignore = channel.getIgnoreTimeout();
        }
        else if ( msg_type == FIND_PARENT || msg_type == FINAL_FIND_PARENT )
        {
            ignore = channel.getStripe( stripe_id ).getIgnoreTimeout();
        }
        else
        {
            System.out.println( "Timeout message received from unknown source" );
            ignore = true;
        }

        if ( !ignore )
        {
            if ( ( num_fails > channel.getTimeouts() ) && ( channel.getTimeouts() != -1 ) )
            {
                /* Exceeded allowable number of retries; generate app-level upcall(?) */
		// Check if local node has attempted the final try, if not attempt it!

		if(!channel.getStripe(stripe_id).getFinalTry()){
		    System.out.println("DEBUG :: Exceeded allowable number of retries , sending ANYCAST to STRIPE tree. From "+scribe.getNodeId()+" for stripe :"+(NodeId)stripe_id);
		    ControlFinalFindParentMessage cffpMsg = new ControlFinalFindParentMessage(scribe.getAddress(),
											      scribe.getLocalHandle(),
											      stripe_id,
											      c, 
											      channel.getChannelId() );
		    scribe.anycast(stripe_id, cffpMsg, c);
		    
		    ControlTimeoutMessage timeoutMessage = new ControlTimeoutMessage( SplitStreamAddress.instance(),
                                                                                      0,
                                                                                      stripe_id,
                                                                                      c,
										      channel_id );
		    float rand = random.nextFloat();
		    float newTimeout = (float)((1 + rand) * channel.getTimeoutLen());
		    channel.getStripe( stripe_id ).num_fails = 0;
		    channel.getStripe( stripe_id ).setFinalTry(true);
                    thePastryNode.scheduleMsg( timeoutMessage, (long)newTimeout);
		}
		
            }
            else
            {
                if ( msg_type == ATTACH && !channel.isReady())
                {
		    System.out.println("DEBUG :: Node "+scribe.getNodeId()+" sending ControlAttachMessage");
        	    ControlAttachMessage attachMessage = new ControlAttachMessage( scribe.getAddress(), scribe.getLocalHandle(), channel_id, c );
                    //channel.getSplitStream().routeMsg( dest, attachMessage, c, null );
		    scribe.anycast(channel_id, attachMessage, c);
                    ControlTimeoutMessage timeoutMessage = new ControlTimeoutMessage( channel.getSplitStream().getAddress(),
                                                                                      num_fails+1,
                                                                                      dest,
                                                                                      c, channel_id );
		    long multiplyTimeout = (long)(1 << (num_fails + 1));
		    float rand = random.nextFloat();
		    multiplyTimeout *= (1.0 + rand);
		    float newTimeout = (float)(multiplyTimeout * channel.getTimeoutLen());
		    System.out.println("DEBUG :: ATTACH MESSAGE TIMED OUT : new time out "+(long)newTimeout+" rand = "+rand+" multiplyTimeout "+multiplyTimeout);
	            thePastryNode.scheduleMsg( timeoutMessage, (long)newTimeout);
                
                }
                else if ( msg_type == FIND_PARENT )
                {
		    System.out.println("DEBUG :: Time out message for FIND_PARENT at "+scribe.getLocalHandle().getNodeId()+" for stripe "+stripe_id);
                    ControlFindParentMessage msg = new ControlFindParentMessage( scribe.getAddress(),
                                                                                 scribe.getLocalHandle(),
                                                                                 dest,
                                                                                 c,
                                                                                 stripe_id, channel_id );
                    //channel.getSplitStream().routeMsg( dest, msg, c, null ); 
		    scribe.anycast(dest, msg, c);
                    ControlTimeoutMessage timeoutMessage = new ControlTimeoutMessage( SplitStreamAddress.instance(),
                                                                                      num_fails+1,
                                                                                      dest,
                                                                                      c, stripe_id, channel_id );
		    channel.getStripe( stripe_id ).num_fails = num_fails + 1;
		    long multiplyTimeout = (long)(1 << (num_fails + 1));
		    float rand = random.nextFloat();
		    multiplyTimeout *= (1.0 + rand);
		    float newTimeout = (float)(multiplyTimeout * channel.getTimeoutLen());
		    System.out.println("DEBUG :: FIND_PARENT MESSAGE TIMED OUT : new time out "+(long)newTimeout+" rand = "+rand+ "multiplyTimeout = "+multiplyTimeout+" num_fails "+num_fails);
                    thePastryNode.scheduleMsg( timeoutMessage, (long)newTimeout);
                }
		else if(msg_type == FINAL_FIND_PARENT){
		    System.out.println("DEBUG :: Time out message for FINAL_FIND_PARENT at "+scribe.getLocalHandle().getNodeId()+" for stripe "+stripe_id);
		    ControlFinalFindParentMessage cffpMsg = new ControlFinalFindParentMessage(scribe.getAddress(),
											      scribe.getLocalHandle(),
											      stripe_id,
											      c, 
											      channel.getChannelId() );
		    scribe.anycast(stripe_id, cffpMsg, c);
		    
		    ControlTimeoutMessage timeoutMessage = new ControlTimeoutMessage( SplitStreamAddress.instance(),
                                                                                      num_fails+1,
                                                                                      stripe_id,
                                                                                      c,
										      channel_id );
		    long multiplyTimeout = (long)(1 << (num_fails + 1));
		    float rand = random.nextFloat();
		    multiplyTimeout *= (1.0 + rand);
		    float newTimeout = (float)(multiplyTimeout * channel.getTimeoutLen());
		    System.out.println("DEBUG :: FINAL_FIND_PARENT MESSAGE TIMED OUT : new time out "+(long)newTimeout+" rand = "+rand);
		    channel.getStripe( stripe_id ).num_fails = num_fails + 1;
                    thePastryNode.scheduleMsg( timeoutMessage, (long)(newTimeout));
		}
            }
        }
    }	
}





