package rice.splitstream;

import rice.pastry.*;

/**
 *
 * @author Ansley Post
 */
public class StripeId extends NodeId{
 public StripeId(NodeId node){
  super(node.copy());
 }	
}
