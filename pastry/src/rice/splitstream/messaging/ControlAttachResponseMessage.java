package rice.splitstream.messaging;

import rice.splitstream.*;
import rice.pastry.messaging.*;
import java.io.Serializable;
/**
 * This message is sent in response to an incoming Attach message.  It
 * contains a list of all Stripe Ids for the current channel, as well as
 * the id of the spare capacity tree and the channel id itself.
 * @author briang
 */
public class ControlAttachResponseMessage extends Message implements Serializable
{
   private Object _content;

   public ControlAttachResponseMessage( Address addr )
   {
      super( addr );
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
}

