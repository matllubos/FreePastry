package rice.splitstream.messaging;
import rice.splitstream.*;
import rice.pastry.*;
/*
 * @(#) StripeContentMessage.java
 *
 */
/**
 * This message is to contain the striped data 
 */
public class StripeContentMessage implements SplitStreamMessage{
     /**
      * Returns the NodeHandle that this message originated from
      * @return NodeHandle The source of the message
      */
      public NodeHandle getSource(){return null;}
     /**
      * This returns the data
      * @return java.io.Serializable the data
      */
     public java.io.Serializable getData(){ return null;}
     /**
      * Sets the data
      * @param java.io.Serializable the data to be used
      */
     public void setData(java.io.Serializable data){}
     /**
      * The call back method into the application for messages to be
      * forwarded.
      * @param splitStream a handle to the application
      * @param s the Stripe which this message belongs to
      */
     public void handleDeliverMessage(ISplitStream splitStream, Stripe s){}
     /**
      * The call back method into the application for messages to be
      * delivered.
      * @param splitStream a handle to the application
      * @param s the Stripe which this message belongs to
      */
     public void handleForwardMessage(ISplitStream splitStream, Stripe s){}
     /**
      * @return a String representation of the Object
      */ 
     public String toString() {return null;}
}
