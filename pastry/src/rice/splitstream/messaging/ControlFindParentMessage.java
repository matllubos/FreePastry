package rice.splitstream.messaging;
import rice.splitstream.*;
import rice.scribe.messaging.*;
import java.util.Vector;

/**
 * This class represents the anycast message sent by a node upon receiving a
 * drop notification from its former parent.  It is sent to the spare
 * capacity tree in an attempt to find a new parent.
 */
public class ControlFindParentMessage extends MessageAnycast
{
    Vector send_to;
    Vector already_seen;

    public ControlFindParentMessage( Address addr, NodeHandle source, StripeId topicId, Credentials c )
    {
       super( addr, source, topicId, c );
       send_to = new Vector();
       already_seen = new Vector();
    }

    /**
     * This method determines whether a given source node is in the path to root of this node for a
     * given stripe tree.
     * @param splitStream This node
     * @param source Source node's handle
     * @param stripe_id Stripe ID for stripe tree to examine over
     */
    private boolean isInRootPath( IScribe scribe, NodeHandle source, StripeId stripe_id )
    {
        return false;
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
    public void handleMessage( SplitStreamImpl splitStream, Stripe s )
    {
        IScribe scribe = splitStream.getScribe();
        int num_distinct_children = scribe.getDistinctChildren().size();
        if ( send_to.size() != 0 )
        {
            already_seen.add( 0, send_to.remove(0) );
        }
        else
        {
            already_seen.add( 0, scribe.getNodeHandle() );
        }

        if ( num_distinct_children > splitStream.getBandwidthManager().getAllowableChildren() )
        {
            if ( !isInRootPath( scribe, s.getStripeId() ) )
            {
                scribe.addChild( this.getSource(), s.getStripeId() );
                scribe.routeMsgDirect( this.getSource(), 
                                       new ControlFindParentResponseMessage( splitStream.getAddress(),
                                                                             scribe.getNodeHandle(),
                                                                             s.getStripeId(),
                                                                             splitStream.getCredentials(),
                                                                             new Boolean( true ) ) );
            }
            if ( scribe.getDistinctChildren().size() > splitStream.getBandwidthManager().getAllowableChildren() )
            {
                /* need to leave the spare capacity tree now */
            }
        }

        Vector v = scribe.getChildren( s.getStripeId() );
        if ( v != null )
        {
            send_to.addAll( 0, scribe.getChildren( s.getStripeId() ) );
        }
        while ( !( already_seen.contains( send_to.get(0) ) ) && ( send_to.size() > 0 ) )
        {
            send_to.remove( 0 );
        }
        if ( send_to.size() > 0 )
        {
            scribe.routeMsgDirect( (NodeHandle)send_to.get(0), this );
        }
        else
        {
            if ( !isRoot( s.getStripeId() ) )
            {
                scribe.routeMsgDirect( scribe.getParent( s.getStripeId() ), this );
            }
            else
            {
                scribe.routeMsgDirect( this.getSource(), 
                                       new ControlFindParentResponseMessage( splitStream.getAddress(),
                                                                             scribe.getNodeHandle(),
                                                                             s.getStripeId(),
                                                                             splitStream.getCredentials(),
                                                                             new Boolean( false ) ) );
            }
        }
    }

    public String toString()
    {
        return null;
    }
}






