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
 */
public class ControlDropMessage extends ControlMessage{

   private NodeId spare_id;
   private StripeId stripe_id;
   private ChannelId channel_id;

   public ControlDropMessage( Address addr, NodeHandle source, NodeId stripe_id,
                              Credentials c, NodeId spare_id, ChannelId channel_id )
   {
      super( addr, source, stripe_id, c );
      this.stripe_id = (StripeId)stripe_id;
      this.spare_id = spare_id;
      this.channel_id = channel_id;
   }

   public StripeId getStripeId()
   {
      return stripe_id;
   }

   /**
    * Can't attach to parent (or dropped from parent for some reason). Anycast
    * to the spare capacity tree to find a new parent.
    * @param scribe The scribe group this message is relevant to
    * @param topic The topic this message is relevant to
    */
   public void handleDeliverMessage( Scribe scribe, Topic topic )
   {
      Credentials c = new PermissiveCredentials();
      ControlFindParentMessage msg = new ControlFindParentMessage( scribe.getAddress(), 
                                                                   scribe.getLocalHandle(),
                                                                   spare_id,
                                                                   c,
                                                                   (StripeId)topic.getTopicId(), channel_id );
      scribe.anycast( spare_id, msg, c );
   }

   /**
    * Should do nothing
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





