package rice.splitstream.messaging;
import rice.splitstream.*;
/**
 * This message is sent by a node to its new parent when it enters
 * the tree for a particular stripe.  The parent then should take
 * action to either allow the new child in, drop it, or drop another
 * one of its children, should the new outgoing bandwidth exceed the
 * configured limitation.
 */
public class ControlAddMessage extends ControlMessage
{

    public ControlAddMessage()
    {
    }

    /**
     * This is the callback method for when this message is accepted
     * by the current node (i.e., this node is the parent of the
     * message's sender).
     * @param splitStream The SplitStream application
     * @param s The stripe that this message is relevant to
     */
    public void handleDeliverMessage( ISplitStream splitStream, Stripe s )
    {
    }

    /**
     * This is the callback method for when this message should be
     * forwarded along the tree; the current node is not the intended
     * recipient of this message.
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
