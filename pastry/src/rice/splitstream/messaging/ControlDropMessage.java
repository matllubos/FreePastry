package rice.splitstream.messaging;

import rice.splitstream.*;
import rice.pastry.*;
import rice.pastry.messaging.*;
import rice.pastry.security.*;
import rice.scribe.*;
import rice.scribe.messaging.*;

/**
 * This message is sent to a client when it has been dropped from it's
 * parent in the tree for a particular stripe. Upon receipt of the message,
 * the client should attempt to locate another parent.
 * @author briang
 */
public class ControlDropMessage extends ControlMessage{

   private NodeId spare_id;
   private StripeId stripe_id;
   private ChannelId channel_id;
   private long timeout_len = 5000;

   public ControlDropMessage( Address addr, NodeHandle source, NodeId stripe_id,
                              Credentials c, NodeId spare_id, ChannelId channel_id, long timeout_len )
   {
      super( addr, source, stripe_id, c );
      this.stripe_id = (StripeId)stripe_id;
      this.spare_id = spare_id;
      this.channel_id = channel_id;
      this.timeout_len = timeout_len;
   }

   /**
    * @return The stripe id associated with this message
    */
   public StripeId getStripeId()
   {
      return stripe_id;
   }

   /**
    * Does nothing; necessary for superclass compatibility
    * 
    * @param scribe The scribe group this message is relevant to
    * @param topic The topic this message is relevant to
    */    
   public void handleDeliverMessage( Scribe scribe, Topic topic )
   {
      System.out.println( "Getting to wrong handleDeliverMessage at node "+scribe.getNodeId() );
   }


   /**
    * This method is called upon receipt of the message by the node that has been
    * dropped.  An anycast ControlFindParentMessage is sent to the spare capacity
    * tree in an attempt to find a new parent.
    * 0
    * @param scribe The scribe group this message is relevant to
    * @param topic The topic this message is relevant to
    * @param thePastryNode Pastry node from the channel this message is delivered to
    */
   public void handleDeliverMessage( Scribe scribe, Topic topic, PastryNode thePastryNode, Channel channel )
   {
      Credentials c = new PermissiveCredentials();
      ControlFindParentMessage msg = new ControlFindParentMessage( SplitStreamAddress.instance(), 
                                                                   scribe.getLocalHandle(),
                                                                   spare_id,
                                                                   c,
                                                                   (StripeId)topic.getTopicId(), channel_id );
      //scribe.anycast( spare_id, msg, c ); 
      channel.routeMsg(spare_id, msg, c, null);
      scribe.setParent(null, topic.getTopicId());
      ControlTimeoutMessage timeoutMessage = new ControlTimeoutMessage( this.getDestination(),
                                                                        0,
                                                                        spare_id,
                                                                        c, stripe_id, channel_id );
      channel.getStripe( stripe_id ).setIgnoreTimeout( false );
      //System.out.println("Node "+scribe.getNodeId()+" sending FIND_PARENT_MESSAGE for "+(StripeId)topic.getTopicId());
      thePastryNode.scheduleMsg( timeoutMessage, timeout_len );
      //System.out.println("setParent set to null called ");
      //System.out.println("Node "+scribe.getNodeId()+ " dropped for "+topic.getTopicId());
   }

   /**
    * Should do nothing
    *
    * @param scribe The Scribe group this message is relevant to
    * @param topic The topic this message is relevant to
    */
   public boolean handleForwardMessage( Scribe scribe, Topic topic )
   {
      return true;
   }

   /**
    * @return A string representation of this object
    */
   public String toString(){return null;}


}





