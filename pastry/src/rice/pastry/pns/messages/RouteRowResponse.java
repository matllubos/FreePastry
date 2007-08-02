package rice.pastry.pns.messages;

import rice.pastry.NodeHandle;
import rice.pastry.messaging.Message;
import rice.pastry.routing.RouteSet;

public class RouteRowResponse extends Message {

  public short index;
  public RouteSet[] row;
  public NodeHandle responder;

  public RouteRowResponse(NodeHandle localHandle, short index, RouteSet[] row, int address) {
    super(address);
    this.responder = localHandle;
    this.index = index;
    this.row = row;
  }

}
