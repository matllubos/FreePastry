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

  protected NodeId _randomNode;

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

    System.out.println("\n\nDEBUG-messageForAppl()---------------------\n");
    System.out.println("Message in node: " + this.getNodeId() + atMsg.toString());
    System.out.println("\n\n-------------------------------------------\n");
    
    super.messageForAppl(atMsg);
  }

  protected void _routeMsg(NodeId dest, AP3Message msg) {
    System.out.println("\n\nDEBUG-_routeMsg()--------------------------\n");
    System.out.println("Sending message from " + this.getNodeId() + " to " + dest);
    System.out.println("\n\n-------------------------------------------\n");
    super._routeMsg(dest, msg);
  }

  /**
   * Overriden to return a specific node as opposed to a random one
   */
  protected NodeId _generateRandomNodeID() {
    return _randomNode;
  }

  protected void setRandomNode(NodeId node) {
    _randomNode = node;
  }

  /**
   * Overriden to return an AP3TestingMessage.
   */
  protected AP3Message _createAP3Message(NodeId source,
					 Object content,
					 int messageType,
					 double fetchProbability) {
    return new AP3TestingMessage(source, content, messageType, fetchProbability);
  }
}
