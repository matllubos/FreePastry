package rice.splitstream.messaging;

import rice.splitstream.*;
import rice.scribe.messaging.*;
import rice.pastry.security.*;
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

    final int ALLOWABLE_CHILDREN = 2;

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
     * This method returns the total number of children of this node, over all topics.
     */
    private int aggregateNumChildren( Scribe scribe )
    {
        Vector topics = scribe.getTopics();
        int total = 0;

        for (int i=0; i<topics.size(); i++)
        {
            total += scribe.numChildren( ((Topic)topics.get(i)).getTopicId() );
        }
    }

    /**
     * This is the callback method for when this message should be forwarded to another
     * node in the spare capacity tree.  This node does not have any spare capacity
     * and is unable to take on the message originator as a child.
     * @param scribe The SplitStream application
     * @param topic The stripe that this message is relevant to
     */
    public void handleForwardMessage( Scribe scribe, Topic topic )
    {
        Credentials c = new PermissiveCredentials();
        
        if ( send_to.size() != 0 )
        {
            already_seen.add( 0, send_to.remove(0) );
        }
        else
        {
            already_seen.add( 0, scribe.getNodeHandle() );
        }

        if ( ( aggregateNumChildren( scribe ) > ALLOWABLE_CHILDREN &&
             ( !isInRootPath( scribe, topic.getTopicId() ) ) )
        {
            this.handleDeliverMessage( scribe, topic );
        }
        else
        {
            Vector v = scribe.getChildren( topic.getTopicId() );
            if ( v != null )
            {
                send_to.addAll( 0, scribe.getChildren( topic.getTopicId() ) );
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
                if ( !isRoot( topic.getTopicId() ) )
                {
                    scribe.routeMsgDirect( scribe.getParent( topic.getTopicId() ), this );
                }
                else
                {
                    scribe.routeMsgDirect( this.getSource(),
                                           new ControlFindParentResponseMessage( scribe.getAddress(),
                                                                                 scribe.getNodeHandle(),
                                                                                 topic.getTopicId(),
                                                                                 c,
                                                                                 new Boolean( false ) ) );
                }
            }
        }
    }

    /**
     * This is the callback method for when this message is accepted
     * by the current node (i.e., this node has the potential to act
     * as a parent to the node that sent the message).  Note that
     * this does not necessarily imply that the current node will
     * fulfill the conditions to take on the message originator as a
     * new child.
     * @param scribe The SplitStream application
     * @param topic The stripe that this message is relevant to
     */
    public void handleDeliverMessage( Scribe scribe, Topic topic )
    {
        Credentials c = new PermissiveCredentials();

        scribe.addChild( this.getSource(), topic.getTopicId() );
        scribe.routeMsgDirect( this.getSource(), 
                               new ControlFindParentResponseMessage( scribe.getAddress(),
                                                                     scribe.getNodeHandle(),
                                                                     topic.getTopicId(),
                                                                     c,
                                                                     new Boolean( true ) ) );

        if ( aggregateNumChildren( scribe ) >= ALLOWABLE_CHILDREN )
        {
            /* need to leave the spare capacity tree now */
        }
    }

    public String toString()
    {
        return null;
    }
}






