package rice.splitstream.messaging;

import rice.splitstream.*;
import rice.pastry.messaging.*;

/**
 * This message is sent in response to an incoming Attach message.  It
 * contains a list of all Stripe Ids for the current channel
 */
public class ControlAttachResponseMessage extends Message implements Serializable
{
   private Object _content;

   public ControlAttachResponseMessage( Address addr )
   {
      super( addr );
   }

   /**
    * Sets the message content (here, the list of StripeIds)
    * @param content The message content
    */
   public void setContent( Object content )
   {
      _content = content;
   }

   /**
    * Gets the message content (here, the list of StripeIds)
    * @return The message content
    */
   public Object getContent()
   {
      return _content;
   } 
}

