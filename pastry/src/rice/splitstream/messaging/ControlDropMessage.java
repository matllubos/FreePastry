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
 *
 * @(#) ControlDropMessage.java
 * @version $Id$
 * @author briang
 * @author Atul Singh
 */
public class ControlDropMessage extends ControlMessage{

   /**
    * Id of the spare capacity tree
    */
   private NodeId spare_id;

   /**
    * Id of the stripe being dropped from
    */
   private StripeId stripe_id;

   /**
    * Id of the channel this message pertains to
    */
   private ChannelId channel_id;

   /**
    * Time to wait before delivery of a timeout message (in ms)
    */
   private long timeout_len = 5000;

   /**
    * Constructor for this message type
    * @param addr The address of the destination application
    * @param source The origin of this message
    * @param stripe_id The stripe being dropped from
    * @param c Credentials to send this message under
    * @param spare_id Id of the spare capacity tree
    * @param channel_id Id of the channel this message pertains to
    * @param timeout_len Length of time to wait before delivery of a timeout message
    */
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

    public ChannelId getChannelId(){
	return channel_id;
    }

   /**
    * Does nothing; necessary for superclass compatibility
    * 
    * @param scribe The scribe group this message is relevant to
    * @param topic The topic this message is relevant to
    */    
   public void handleDeliverMessage( Scribe scribe, Topic topic )
   {
      System.out.println( "DEBUG :: Getting to wrong handleDeliverMessage at node "+scribe.getNodeId() );
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
       Stripe stripe = channel.getStripe(stripe_id);

       System.out.println("DEBUG :: Rec DROP Message :: Sending FindParentMessage from "+scribe.getLocalHandle().getNodeId()+" for stripe "+topic.getTopicId());
      ControlFindParentMessage msg = new ControlFindParentMessage( scribe.getAddress(),
                                                                   scribe.getLocalHandle(),
                                                                   spare_id,
                                                                   c,
                                                                   (StripeId)topic.getTopicId(), channel_id );
      // set the path to null and parent as null.
      scribe.anycast(spare_id, msg, c);
      channel.getStripe( stripe_id ).setRootPath(null);
      scribe.setParent(null, topic.getTopicId());

      ControlTimeoutMessage timeoutMessage = new ControlTimeoutMessage( this.getDestination(),
                                                                        0,
                                                                        spare_id,
                                                                        c, stripe_id, channel_id );
      channel.getStripe( stripe_id ).num_fails = 0;
      channel.getStripe( stripe_id ).setIgnoreTimeout( false );
      float rand = Channel.random.nextFloat();
      float newTimeout = (float)((1.0 + rand) * timeout_len ); 
      thePastryNode.scheduleMsg( timeoutMessage, (long)newTimeout);
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





