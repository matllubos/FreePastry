package rice.splitstream2.messaging;

import rice.pastry.messaging.*;
import rice.pastry.security.*;
import rice.pastry.*;
import rice.scribe.*;
import rice.splitstream2.*;
import java.util.Vector;

/**
 * This message is sent from any node whose parent has changed, to each
 * of its children and descendents.  It contains a list with the nodeId
 * of each node it encounters along its path.
 *
 * @(#) ControlPropogatePathMessage.java
 * @version $Id$
 * @author briang
 */
public class ControlPropogatePathMessage extends Message{

   /**
    * Cumulative path to root thus far
    */
   private Vector path;
   
   /**
    * Source of this message
    */
   private NodeHandle source;

   /**
    * Id of the stripe this message pertains to
    */
   private StripeId stripe_id;

   /**
    * Id of the channel this message pertains to
    */
   private ChannelId channel_id;

   /**
    * Constructor for a ControlPropogatePath message
    * @param addr Address of the destination application
    * @param source The source of this message
    * @param topicId The stripe we build the path for
    * @param c The credentials to send under
    * @param path Initial path starting point
    * @param channel_id The channel this message pertains to
    */
   public ControlPropogatePathMessage( Address addr, NodeHandle source, NodeId topicId, Credentials c, Vector path, ChannelId channel_id )
   {
       super( addr );
      this.path = path;
      this.source = source;
      this.stripe_id = (StripeId)topicId;
      this.channel_id = channel_id;
   }

   /**
    * @return The stripe id associated with this message
    */
   public StripeId getStripeId()
   {
      return stripe_id;
   }

   public ChannelId getChannelId()
   {
      return channel_id;
   }

   /**
    * Handles forwarding of the message.  This node's path is set
    * to the current list.  Then this node's NodeHandle is added to the
    * list and messages containing the new list are sent to all of this
    * node's children.
    * @param scribe The scribe group this message is relevant to
    * @param channel The channel this message is relevant to
    * @param stripe The specific stripe this is relevant to
    */
   public void handleMessage( Scribe scribe, Channel channel, Stripe stripe )
   {
   }

}






