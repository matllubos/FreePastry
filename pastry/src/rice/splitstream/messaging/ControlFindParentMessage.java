package rice.splitstream.messaging;
import rice.splitstream.*;
import rice.scribe.messaging.*;

/**
 * This class represents the anycast message sent by a node upon receiving a
 * drop notification from its former parent.  It is sent to the spare
 * capacity tree in an attempt to find a new parent.
 */
public class ControlFindParentMessage extends MessageAnycast
{

    public ControlFindParentMessage()
    {
       super(null,null,null,null);
    }

    /**
     * This is the callback method for when this message is accepted
     * by the current node (i.e., this node has the potential to act
     * as a parent to the node that sent the message).  Note that
     * this does not necessarily imply that the current node will
     * fulfill the conditions to take on the message originator as a
     * new child.
     * @param splitStream The SplitStream application
     * @param s The stripe that this message is relevant to
     */
    public void handleMessage( ISplitStream splitStream, Stripe s )
    {
    }

    public String toString()
    {
        return null;
    }
}
