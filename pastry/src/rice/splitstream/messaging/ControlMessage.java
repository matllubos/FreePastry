package rice.splitstream.messaging;
import rice.splitstream.*;
import rice.pastry.*;
/**
 * This is a generic control message type. It is left to the specific
 * application to decide if and how it wants to subclass this for
 * additional control functionality.
 */
public abstract class ControlMessage implements SplitStreamMessage{
   /**
   * Returns the NodeHandle that this message originated from
   * @return NodeHandle The source of the message
   */
   public NodeHandle getSource(){return null;}


   /**
    * Callback method executed when the application receives a message for delivery
    * @param splitStream The application receiving the message
    * @param s The stripe that sent this message
    */
   public abstract void handleDeliverMessage( ISplitStream splitStream, Stripe s );

   /**
    * Callback method executed when the application receives a message for forwarding
    * @param splitStream The application receiving the message
    * @param s The stripe that sent this message
    */
   public abstract void handleForwardMessage( ISplitStream splitStream, Stripe s );

   /**
    * @return A string representation of this object
    */
   public String toString(){return null;}
}
