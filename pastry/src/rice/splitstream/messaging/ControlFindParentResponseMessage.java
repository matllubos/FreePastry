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
public class ControlFindParentResponseMessage extends ControlMessage
{

    public ControlFindParentResponseMessage( Address addr, NodeHandle source, StripeId topicId, Credentials c, Boolean accept )
    {
        super( addr, source, topicId, c );
        this.setData(accept);
    }

    /**
     * This node is the message originator.  It should set its parent to
     * the Id of the originator of this message or, in the case of a "false"
     * response, generate an application-level upcall.
     * @param scribe The Scribe group this message is relevant to
     * @param s The stripe that this message is relevant to
     */
    public void handleDeliverMessage( Scribe scribe, Topic topic )
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

    /**
     * Should do nothing.
     * @param splitStream The Scribe group this message is relevant to
     * @param s The stripe that this message is relevant to
     */
    public boolean handleForwardMessage( Scribe scribe, Topic topic )
    {
       return true;
    }

    public String toString()
    {
        return null;
    }
}



