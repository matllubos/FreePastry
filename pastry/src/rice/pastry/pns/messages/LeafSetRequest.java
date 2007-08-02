package rice.pastry.pns.messages;

import rice.pastry.NodeHandle;
import rice.pastry.messaging.Message;

public class LeafSetRequest extends Message {
  public NodeHandle requestor;
  
  public LeafSetRequest(NodeHandle nodeHandle, int dest) {
    super(dest);
    this.requestor = nodeHandle;
  }

}
