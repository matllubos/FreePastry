package rice.splitstream.messaging;
import rice.splitstream.*;
import rice.pastry.*;
import rice.pastry.messaging.*;
import rice.scribe.*;
import rice.scribe.messaging.*;

/**
 * This is a generic control message type. It is left to the specific
 * application to decide if and how it wants to subclass this for
 * additional control functionality.
 */
public abstract class ControlMessage extends ScribeMessage implements SplitStreamMessage{

   /**
    * Callback method executed when the application receives a message for delivery
    * @param scribe The scribe group this message is relevant to
    * @param s The stripe that sent this message
    */
   public abstract void handleDeliverMessage( Scribe scribe, Topic topic );

   /**
    * Callback method executed when the application receives a message for forwarding
    * @param splitStream The scribe group this message is relevant to
    * @param s The stripe that sent this message
    */
   public abstract void handleForwardMessage( Scribe scribe, Topic topic );

   /**
    * @return A string representation of this object
    */
   public String toString(){return null;}
}
