package rice.splitstream;
import rice.pastry.*;
public class StripeId extends NodeId{
 public StripeId(NodeId node){
  super(node.copy());
 }	
}
