package rice.splitstream;
import rice.pastry.*;
public class SpareCapacityId extends NodeId{
 public SpareCapacityId(NodeId node){
  super(node.copy());
 }	
}
