package rice.splitstream.messaging;
import rice.splitstream.*;

import rice.pastry.*;
import rice.pastry.messaging.*;
import rice.pastry.security.*;

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
 * @version $Id:
 * @author briang
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
     * Constructor for a ControlFindParentResponse
     * @param addr Address of the destination application
     * @param source The source of this message
     * @param channel_id The channel this message pertains to
     * @param c Credentials to send under
     * @param accept Was the potential child accepted or not?
     * @param stripe_id The stripe this message pertains to
     * @param rootPath The new path to root for the child
     */
    public ControlFindParentResponseMessage( Address addr, NodeHandle source, ChannelId channel_id, Credentials c, Boolean accept, StripeId stripe_id, Vector rootPath )
    {
        super( addr );
        m_data = accept;
	this.stripe_id = stripe_id;
        this.source = source;
	m_rootPath = rootPath;
        this.channel_id = channel_id;
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
	    //System.out.println("setparent set");
	    //System.out.println("Node "+scribe.getNodeId()+" received response to FindParent from "+source.getNodeId()+ " for stripe "+topic.getTopicId());
        }
        else
	    {
		System.out.println("CFPResponse -- failed, should retry");
		stripe.setIgnoreTimeout( false );
            /* generate upcall */
        }
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









