package rice.p2p.splitstream;

import rice.pastry.*;

/**
 * This class wraps the nodeId object so we can use type checking
 * and allow more readable and understandable code. All it does is
 * subclass the nodeId and provide a constructor that allows the
 * wrapping of a NodeId object to create a concrete subclass
 *
 * @version $Id$
 * @author Ansley Post
 */
public class StripeId extends NodeId {

 /**
  * Constructor that takes in a nodeId and makes a StripeId
  */
 public StripeId(NodeId node){
  super(node.copy());
 }	

}
