package rice.ap3.testing;

import rice.pastry.client.*;
import rice.pastry.security.*;
import rice.pastry.messaging.*;
import rice.pastry.routing.*;
import rice.pastry.*;
import rice.pastry.direct.*;
import rice.pastry.standard.*;

import rice.ap3.*;
import rice.ap3.routing.*;
import rice.ap3.messaging.*;

import java.util.Hashtable;
import java.util.Random;

/**
 * @(#) AP3TestingService.java
 *
 * A service specially used for testing only.
 * It provides access to the internals of AP3ServiceImpl.
 *
 * @version $Id$
 # @author Gaurav Oberoi
 */
public class AP3TestingService extends AP3ServiceImpl {

  /**
   * Constructor
   */
  public AP3TestingService(PastryNode pn, AP3Client client) {
    super(pn, client);
  }

  /**
   * Exposes the routing table for testing purposes.
   */
  public AP3RoutingTable getAP3RoutingTable() {
    return _routingTable;
  }

  public void messageForAppl(Message msg) {
    AP3TestingMessage atMsg = (AP3TestingMessage) msg;
    atMsg.addRouteInfo(this.getNodeId());
    super.messageForAppl(msg);
  }

  /**
   * Overriden to return an AP3TestingMessage.
   */
  protected AP3Message _createAP3Message(NodeId source,
					 Object content,
					 int messageType,
					 double fetchProbability,
					 Object data) {
    return new AP3TestingMessage(source, content, messageType, fetchProbability);
  }
}

