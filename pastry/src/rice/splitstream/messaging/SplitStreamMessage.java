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
       * Returns the NodeHandle that this message originated from
       * @return NodeHandle The source of the message
       */
      public NodeHandle getSource();
      /**
       * The call back method into the application for messages to be
       * forwarded.
       * @param splitStream a handle to the application
       * @param s the Stripe which this message belongs to
       */
      public void handleDeliverMessage(ISplitStream splitStream, Stripe s);
      /**
       * The call back method into the application for messages to be
       * delivered.  
       * @param splitStream a handle to the application
       * @param s the Stripe which this message belongs to
       */
      public void handleForwardMessage(ISplitStream splitStream, Stripe s);
      /**
       * @return a String representation of the Object
       */ 
      public abstract String toString();
}
