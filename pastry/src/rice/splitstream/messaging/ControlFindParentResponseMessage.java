package rice.splitstream.messaging;
import rice.splitstream.*;

/**
 * This message is sent to the originator of a FindParent message by the
 * parent that has accepted it as a child.  Content is normally true, but
 * in the event that no suitable parent could be found, a "false" message
 * will be sent by the root of the spare capacity tree.
 */
public class ControlFindParentResponseMessage extends ControlMessage
{
   
    public ControlFindParentMessage( Address addr, NodeHandle source, StripeId topicId, Credentials c, Boolean accept )
    {
        super( addr, source, topicId, c );
        m_data = accept;
    }

    /**
     * This node is the message originator.  It should set its parent to
     * the Id of the originator of this message or, in the case of a "false"
     * response, generate an application-level upcall.
     * @param scribe The Scribe group this message is relevant to
     * @param s The stripe that this message is relevant to
     */
    public void handleDeliverMessage( IScribe scribe, Stripe s )
    {
        if ( (Boolean)m_data.booleanValue() )
        {
            scribe.addParent( this.getSource() );
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
    public void handleForwardMessage( IScribe scribe, Stripe s )
    {
    }

    public String toString()
    {
        return null;
    }
}
