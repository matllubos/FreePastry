package rice.splitstream.messaging;

import rice.splitstream.*;
import rice.pastry.messaging.*;
import java.io.Serializable;
/**
 * This message is sent in response to an incoming Attach message.  It
 * contains a list of all Stripe Ids for the current channel, as well as
 * the id of the spare capacity tree and the channel id itself.
 *
 * @(#) ControlAttachResponseMessage.java
 * @version $Id:
 * @author briang
 */
public class ControlAttachResponseMessage extends Message implements Serializable
{

   /**
    * Content to be sent in this message; here, an array of channel information
    * (stripe id's channel id, and spare capacity id)
    */
   private Object _content;

   /**
    * Id of the channel this message pertains to
    */
   private ChannelId channel_id;

   /**
    * Constructor for this message type
    * @param addr The address of the message's destination application
    * @param channel_id The id of the channel this message pertains to
    */
   public ControlAttachResponseMessage( Address addr, ChannelId channel_id )
   {
      super( addr );
      this.channel_id = channel_id;
   }

   /**
    * Sets the message content (here, the combined list of StripeIds, spare
    * capacity id, and channel id)
    *
    * @param content The message content
    */
   public void setContent( Object content )
   {
      _content = content;
   }

   /**
    * Gets the message content (here, the combined list of StripeIds, spare
    * capacity id, and channel id)
    *
    * @return The message content
    */
   public Object getContent()
   {
      return _content;
   }

   public ChannelId getChannelId()
   {
      return channel_id;
   }
}

