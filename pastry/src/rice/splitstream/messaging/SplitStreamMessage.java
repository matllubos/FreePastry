package rice.splitstream.messaging;
import rice.splitstream.*;
import rice.pastry.*;
/*
 * @(#) SplitStreamMessage.java
 *
 */
/**
 * This message is the base class for all SplitStreamMessages 
 */
public interface SplitStreamMessage{

      /**
       * The call back method into the application for messages to be
       * forwarded.
       * @param scribe The scribe group this message is relevant to
       * @param s the Stripe which this message belongs to
       */
      public void handleDeliverMessage(IScribe scribe, Stripe s);
      /**
       * The call back method into the application for messages to be
       * delivered.  
       * @param splitStream The scribe group this message is relevant to
       * @param s the Stripe which this message belongs to
       */
      public void handleForwardMessage(IScribe scribe, Stripe s);
      /**
       * @return a String representation of the Object
       */ 
      public abstract String toString();
}
