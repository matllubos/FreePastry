package rice.splitstream.messaging;
import rice.splitstream.*;
/**
 * This class represents the message sent by a node upon receiving a
 * drop notification from its former parent.  It is sent to the spare
 * capacity tree in an attempt to find a new parent.
 */
public class ControlFindParentResponseMessage extends ControlMessage
{

    public ControlFindParentResponseMessage()
    {
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
    public void handleDeliverMessage( ISplitStream splitStream, Stripe s )
    {
    }

    /**
     * This is the callback method for when this message should be
     * forwarded along the tree; the current node does not even have
     * the potential to act as a parent to the node that sent the
     * message.
     * @param splitStream The SplitStream application
     * @param s The stripe that this message is relevant to
     */
    public void handleForwardMessage( ISplitStream splitStream, Stripe s )
    {
    }

    public String toString()
    {
        return null;
    }
}
