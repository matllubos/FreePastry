package rice.splitstream;
import rice.pastry.*;
/**
 * This class just wraps the NodeId so that
 * we can have type checking.
 */
public class ChannelId extends NodeId{
 /**
  * Constructor that takes in a nodeId and makes a ChannelId
  */
 public ChannelId(NodeId node){
	super(node.copy()); 
 }	
}
