package rice.splitstream.messaging;
import rice.splitstream.*;

import rice.pastry.*;
import rice.pastry.messaging.*;
import rice.pastry.security.*;
import rice.pastry.direct.*;

import java.lang.Boolean;
import java.util.Vector;

import rice.scribe.*;
import rice.scribe.messaging.*;

/**
 * This message is sent to the originator of a FindParent message by the
 * parent that has accepted it as a child.  Content is normally true, but
 * in the event that no suitable parent could be found, a "false" message
 * will be sent by the root of the spare capacity tree.
 *
 * @(#) ControlFindParentResponseMessage.java
 * @version $Id$
 * @author briang
 * @author Atul Singh
 *
 * @deprecated This version of SplitStream has been deprecated - please use the version
 *   located in the rice.p2p.splitstream package.
 */


public class ControlFindParentResponseMessage extends Message
{
    /**
     * Id of the stripe accepted for
     */
    StripeId stripe_id;

    /**
     * Source of this message
     */
    public NodeHandle source;

    /**
     * Data associated with this message (here, is
     * this an acceptance message or a final rejection)
     */
    Object m_data;

    /**
     * New path to root
     */
    Vector m_rootPath;

    /**
     * Channel this message pertains to
     */
    ChannelId channel_id;


    /**
     * Flag to suggest whether receiving node should
     * attempt final try to rejoin by sending 
     * ControlFinalFindParentMessage
     */
    boolean final_try;

    /**
     * Constructor for a ControlFindParentResponse
     * @param addr Address of the destination application
     * @param source The source of this message
     * @param channel_id The channel this message pertains to
     * @param c Credentials to send under
     * @param accept Was the potential child accepted or not?
     * @param stripe_id The stripe this message pertains to
     * @param rootPath The new path to root for the child
     */
    public ControlFindParentResponseMessage( Address addr, NodeHandle source, ChannelId channel_id, Credentials c, Boolean accept, StripeId stripe_id, Vector rootPath, boolean finaltry )
    {
        super( addr );
        m_data = accept;
	this.stripe_id = stripe_id;
        this.source = source;
	m_rootPath = rootPath;
        this.channel_id = channel_id;
	this.final_try = finaltry;
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
     * Check if we need to send FinalFindParentMessage
     */
    public boolean checkFinalTry(){
	return final_try;
    }
    /**
     * This node is the message originator.  It should set its parent to
     * the Id of the originator of this message or, in the case of a "false"
     * response, generate an application-level upcall.
     * @param scribe The Scribe group this message is relevant to
     * @param topic The stripe that this message is relevant to
     * @param stripe The stripe associated with the message
     */
    public void handleMessage( Scribe scribe, Topic topic, Stripe stripe )
    {
        if ( ((Boolean)m_data).booleanValue() )
        {
            scribe.setParent( source, topic.getTopicId() );
	    stripe.setIgnoreTimeout( true );
	    stripe.setState(Stripe.STRIPE_SUBSCRIBED);
	    stripe.setFinalTry(false);
	    //System.out.println(" DEBUG ::setparent set");
	    System.out.println(" DEBUG :: CFPResponse -- passed, Node "+scribe.getNodeId()+" received response to FindParent from "+source.getNodeId()+ " for stripe "+topic.getTopicId()+" at time "+(int)System.currentTimeMillis());
        }
        else
	    {
		Channel channel = stripe.getChannel();
		int num_fails = stripe.num_fails;
		int max_fails = channel.getMaxTimeout();
		
		System.out.println("DEBUG :: CFPResponse -- failed, max fails = "+max_fails +" num failed already = "+num_fails+" at "+scribe.getNodeId()+ " for topic "+stripe.getStripeId());


		if(max_fails <= 0){
		    if(!stripe.getFinalTry()){
			System.out.println(" DEBUG ::CFPResponse -- failed, should retry. Source "+scribe.getNodeId()+" Topic "+stripe.getStripeId());
			
			Credentials c = new PermissiveCredentials();
			
			// Policy for handling failures in FindParentMessage
			// Now, no-one could take me as a child in spare-capacity tree,
			// so now should go back to the first node in stripe it contacted
			// first, go down that stripe tree to the leaves, and ask them
			// (if they have positive outgoing capacity) to drop one of their
			// primary children and take me.
			
			System.out.println(" DEBUG ::Sending controlFinalFindParentMessage from "+scribe.getNodeId()+" for stripe "+stripe.getStripeId());
			ControlFinalFindParentMessage cffpMsg = new ControlFinalFindParentMessage(scribe.getAddress(),
												  scribe.getLocalHandle(),
												  stripe.getStripeId(), 
												  c, 
												  channel.getChannelId() );
			scribe.anycast(stripe.getStripeId(), cffpMsg, c);
			
			ControlTimeoutMessage timeoutMessage = new ControlTimeoutMessage( SplitStreamAddress.instance(),
											  0,//num_fails+1,
											  stripe.getStripeId(),
											  c,
											  channel_id );
			float rand = Channel.random.nextFloat();
			float newTimeout = (float)((1.0 + rand) * channel.getTimeoutLen());
			stripe.num_fails = 0;
			stripe.setFinalTry(true);

			scribe.getPastryNode().scheduleMsg( timeoutMessage, (long)newTimeout);
			stripe.setIgnoreTimeout( false );
		    }
		    else{
			System.out.println(" DEBUG ::PANIC --- FinalFindParentMessage also Failed at "+scribe.getNodeId()+" for stripe "+stripe.getStripeId());
		    }
		}
		else {
		    if(num_fails > max_fails && stripe.getFinalTry()){
			System.out.println(" DEBUG ::PANIC --- FinalFindParentMessage also Failed at "+scribe.getNodeId()+" for stripe "+stripe.getStripeId());
		    }

		}
		
	    }
    
		 
		/*
		  if(max_fails > 0 && num_fails < max_fails)
		    stripe.setIgnoreTimeout( false );
		//else if(checkFinalTry()){
		else if(!stripe.getFinalTry()){
		    System.out.println(" DEBUG ::CFPResponse -- failed, should retry. Source "+scribe.getNodeId()+" Topic "+stripe.getStripeId());
		    
		    Credentials c = new PermissiveCredentials();
		    
		    // Policy for handling failures in FindParentMessage
		    // Now, no-one could take me as a child in spare-capacity tree,
		    // so now should go back to the first node in stripe it contacted
		    // first, go down that stripe tree to the leaves, and ask them
		    // (if they have positive outgoing capacity) to drop one of their
		    // primary children and take me.
		    
		    System.out.println(" DEBUG ::Sending controlFinalFindParentMessage from "+scribe.getNodeId()+" for stripe "+stripe.getStripeId());
		    ControlFinalFindParentMessage cffpMsg = new ControlFinalFindParentMessage(scribe.getAddress(),
											      scribe.getLocalHandle(),
											      stripe.getStripeId(), 
											      c, 
											      channel.getChannelId() );
		    scribe.anycast(stripe.getStripeId(), cffpMsg, c);

		    ControlTimeoutMessage timeoutMessage = new ControlTimeoutMessage( SplitStreamAddress.instance(),
                                                                                      0,//num_fails+1,
                                                                                      stripe.getStripeId(),
                                                                                      c,
										      channel_id );
		    stripe.num_fails = 0;
		    stripe.setFinalTry(true);
                    scribe.getPastryNode().scheduleMsg( timeoutMessage, channel.getTimeoutLen() );
		    stripe.setIgnoreTimeout( false );
		    }
		else{
		    System.out.println(" DEBUG ::PANIC --- FinalFindParentMessage also Failed at "+scribe.getNodeId()+" for stripe "+stripe.getStripeId());
		    stripe.setIgnoreTimeout( true );
		}
		*/
            /* generate upcall */
	        
    }
    
    public NodeHandle getSource(){
	return source;
    }

    public Vector getPath(){
	return m_rootPath;
    }

    public String toString()
    {
        return null;
    }
}









