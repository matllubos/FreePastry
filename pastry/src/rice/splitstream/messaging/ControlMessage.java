package rice.splitstream.messaging;
import rice.splitstream.*;
import rice.pastry.*;
import rice.pastry.messaging.*;

/**
 * This is a generic control message type. It is left to the specific
 * application to decide if and how it wants to subclass this for
 * additional control functionality.
 */
public abstract class ControlMessage extends Message implements SplitStreamMessage{

   /**
    * Callback method executed when the application receives a message for delivery
    * @param splitStream The application receiving the message
    * @param s The stripe that sent this message
    */
   public abstract void handleDeliverMessage( SplitStreamImpl splitStream, Stripe s );

   /**
    * Callback method executed when the application receives a message for forwarding
    * @param splitStream The application receiving the message
    * @param s The stripe that sent this message
    */
   public abstract void handleForwardMessage( SplitStreamImpl splitStream, Stripe s );

   /**
    * @return A string representation of this object
    */
   public String toString(){return null;}
}
