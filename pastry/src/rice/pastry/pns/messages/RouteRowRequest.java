package rice.pastry.pns.messages;

import rice.pastry.NodeHandle;
import rice.pastry.messaging.Message;

public class RouteRowRequest extends Message {
  public NodeHandle requestor;
  public short index;
  
  public RouteRowRequest(NodeHandle nodeHandle, short index, int dest) {
    super(dest);
    this.requestor = nodeHandle;
    this.index = index;
  }

}
