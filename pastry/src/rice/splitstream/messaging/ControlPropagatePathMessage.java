/**
 * This message is sent from any node whose parent has changed, to each
 * of its children and descendents.  It contains a stack with the nodeId
 * of each node it encounters along its path.
 */
public class ControlPropagatePathMessage extends ControlMessage{

   /**
    * Handles forwarding of the message: adding this node's Id to the stack
    * and sending to all children
    * @param splitStream The SplitStream app
    * @param s The specific stripe this is relevant to
    */
   public void handleForwardMessage( ISplitStream splitStream, Stripe s){}

   /**
    * Handles delivery of the message: should do nothing
    * @param splitStream The SplitStream app
    * @param s The specific stripe this is relevant to
    */
   public void handleDeliverMessage( ISplitStream splitStream, Stripe s){}
}
