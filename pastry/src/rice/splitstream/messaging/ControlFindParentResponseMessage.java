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
    StripeId stripe_id;
    public NodeHandle source;
    Object m_data;
    Vector m_rootPath;

    public ControlFindParentResponseMessage( Address addr, NodeHandle source, ChannelId topicId, Credentials c, Boolean accept, StripeId stripe_id, Vector rootPath )
    {
        super( addr );
        m_data = accept;
	this.stripe_id = stripe_id;
        this.source = source;
	m_rootPath = rootPath;
    }

    public StripeId getStripeId()
    {
        return stripe_id;
    }

    /**
     * This node is the message originator.  It should set its parent to
     * the Id of the originator of this message or, in the case of a "false"
     * response, generate an application-level upcall.
     * @param scribe The Scribe group this message is relevant to
     * @param s The stripe that this message is relevant to
     */
    public void handleMessage( Scribe scribe, Topic topic )
    {
        if ( ((Boolean)m_data).booleanValue() )
        {
            scribe.setParent( source, topic.getTopicId() );
	    //System.out.println("setparent set");
	    //System.out.println("Node "+scribe.getNodeId()+" received response to FindParent from "+source.getNodeId()+ " for stripe "+topic.getTopicId());
        }
        else
        {
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









