package rice.splitstream.messaging;
import rice.splitstream.*;
/**
 * This message is sent to a client when it has been dropped from it's
 * parent in the tree for a particular stripe. Upon receipt of the message,
 * the client should attempt to locate another parent.
 */
public class ControlDropMessage extends ControlMessage{

   /**
    * Callback method executed when the application receives a message for delivery
    * @param splitStream The application receiving the message
    * @param s The stripe that sent this message
    */
   public void handleDeliverMessage( ISplitStream splitStream, Stripe s ){}

   /**
    * Callback method executed when the application receives a message for forwarding
    * @param splitStream The application receiving the message
    * @param s The stripe that sent this message
    */
   public void handleForwardMessage( ISplitStream splitStream, Stripe s ){}

   /**
    * @return A string representation of this object
    */
   public String toString(){return null;}

   /**
    * Returns the particular stripe that the client was dropped from.
    * @return A stripe
    */
   public Stripe getDroppedStripe(){return null;}
}





