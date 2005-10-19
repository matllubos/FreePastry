package rice.splitstream;

import rice.pastry.*;

/**
 * This class wraps the nodeId object so we can use type checking
 * and allow more readable and understandable code. All it does is
 * subclass the nodeId and provide a constructor that allows the
 * wrapping of a NodeId object to create a concrete subclass 
 *
 * @version $Id$
 * @author Ansley Post
 *
 * @deprecated This version of SplitStream has been deprecated - please use the version
 *   located in the rice.p2p.splitstream package.
 */
public class ChannelId extends NodeId{
    private NodeId nodeId;
 /**
  * Constructor that takes in a nodeId and makes a ChannelId
  */
 public ChannelId(NodeId node){
	super(node.copy()); 
	nodeId = node;
 }	

    public NodeId getNodeId(){
	return nodeId;
    }
}
