package rice.splitstream.messaging;
import rice.splitstream.*;
import rice.pastry.*;
import rice.pastry.messaging.*;
import rice.pastry.security.*;
import java.lang.Boolean;
import rice.scribe.*;
import rice.scribe.messaging.*;

/**
 * This message is sent to the originator of a FindParent message by the
 * parent that has accepted it as a child.  Content is normally true, but
 * in the event that no suitable parent could be found, a "false" message
 * will be sent by the root of the spare capacity tree.
 */


public class ControlFindParentResponseMessage extends Message
{
    StripeId stripe_id;
    public ControlFindParentResponseMessage( Address addr, NodeHandle source, ChannelId topicId, Credentials c, Boolean accept, StripeId stripe_id )
    {
        super( addr, source, topicId, c );
        this.setData(accept);
	this.stripe_id = stripe_id;
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
        if ( ((Boolean)this.getData()).booleanValue() )
        {
            scribe.setParent( this.getSource(), topic.getTopicId() );
        }
        else
        {
            /* generate upcall */
        }
    }

    public String toString()
    {
        return null;
    }
}



