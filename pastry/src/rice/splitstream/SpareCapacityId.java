package rice.splitstream;
import rice.pastry.*;
/**
 *
 * @author Ansley Post
 */
public class SpareCapacityId extends NodeId{
 public SpareCapacityId(NodeId node){
  super(node.copy());
 }	
}
