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
       * @param splitStream a handle to the application
       * @param s the Stripe which this message belongs to
       */
      public void handleDeliverMessage(SplitStreamImpl splitStream, Stripe s);
      /**
       * The call back method into the application for messages to be
       * delivered.  
       * @param splitStream a handle to the application
       * @param s the Stripe which this message belongs to
       */
      public void handleForwardMessage(SplitStreamImpl splitStream, Stripe s);
      /**
       * @return a String representation of the Object
       */ 
      public abstract String toString();
}
