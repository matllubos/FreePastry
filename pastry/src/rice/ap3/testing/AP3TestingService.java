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
 * A service specially used for testing only.
 * It provides access to the internals of AP3ServiceImpl.
 *
 * @version $Id$
 # @author Gaurav Oberoi
 */
public class AP3TestingService extends AP3ServiceImpl {

  protected NodeId _randomNode;

  protected double _fetchProb = -1;

  /**
   * Whether to automatically suspend the routeMsg call to
   * allow this node to be inspected.
   */
  protected boolean suspendRouteMsg = false;

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
    // Update fetch probability for this node, if set for testing
    if (_fetchProb != -1) {
      atMsg.setFetchProbability(_fetchProb);
    }

    System.out.println("\n\nDEBUG-messageForAppl()---------------------\n");
    System.out.println("Message in node: " + this.getNodeId() + atMsg.toString());
    System.out.println("\n\n-------------------------------------------\n");
    
    super.messageForAppl(atMsg);
  }


  /* Fields for suspending operation of routeMsg.
   * This allows us to poke around in nodes during intermediate hops.
   */
  protected NodeId _routeMsgDest = null;
  protected AP3Message _routeMsgMsg = null;

  /**
   * Overridden to suspend operation to allow testing.
   * Resume operation with _resumeRouteMsg().
   */
  protected void _routeMsg(NodeId dest, AP3Message msg) {
    // Save arguments
    _routeMsgDest = dest;
    _routeMsgMsg = msg;

    // Allow automatic resuming
    if (!this.suspendRouteMsg) {
      _resumeRouteMsg();
    }
    else {
      System.out.println("\n\nDEBUG-_routeMsg()--------------------------\n");
      System.out.println("Saving arguments and suspending operation...");
      System.out.println("\n\n-------------------------------------------\n");
    }
  }

  protected void _resumeRouteMsg() {
    System.out.println("\n\nDEBUG-_routeMsg()--------------------------\n");
    System.out.println("Sending message from " + this.getNodeId() + " to " + _routeMsgDest);
    System.out.println("\n\n-------------------------------------------\n");
    super._routeMsg(_routeMsgDest, _routeMsgMsg);
  }

  /**
   * Overriden to return a specific node as opposed to a random one
   */
  protected NodeId _generateRandomNodeID() {
    return _randomNode;
  }

  protected void setDestinationNode(NodeId node) {
    _randomNode = node;
  }

  protected void setFetchProbability(double fetchProb) {
    _fetchProb = fetchProb;
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
