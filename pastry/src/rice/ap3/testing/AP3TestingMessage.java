package rice.ap3.testing;

import rice.ap3.messaging.AP3Message;

import rice.pastry.NodeId;
import rice.pastry.messaging.Message;

import java.util.*;

/**
 * @(#) AP3TestingMessage.java
 *
 * A message in the AP3 system used for testing.
 *
 * @version $Id$
 * @author Gaurav Oberoi
 */
public class AP3TestingMessage extends AP3Message {

  /**
   * A vector of ids used to keep track of
   * where the message has been.
   */
  protected Vector _routeInfo;

  /**
   * Constructor.
   */
  public AP3TestingMessage(NodeId source,
			   Object content,
			   int messageType,
			   double fetchProbability) {
    super(source, content, messageType, fetchProbability);
    _routeInfo = new Vector();
  }

  /**
   * Returns the vector of ids representing this message's route thus far.
   */
  public Vector getRouteInfo() {
    return _routeInfo;
  }

  /**
   * Sets the vector of ids representing this message's route thus far.
   */
  public void setRouteInfo(Vector routeInfo) {
    _routeInfo = routeInfo;
  }

  /**
   * Adds an id to the route of this message.
   */
  public void addRouteInfo(NodeId id) {
    _routeInfo.addElement(id);
  }

  /**
   * Returns the number of hops this message
   * has taken according to routeInfo.
   */
  public int routeLength() {
    return _routeInfo.size();
  }

  public String toString() {
    String routeStr = "[";
    Iterator it = _routeInfo.iterator();
    while(it.hasNext()) {
      NodeId id = (NodeId) it.next();
      routeStr = routeStr + id.toString();
      if(it.hasNext()) {
	routeStr += ",";
      }
    }
    routeStr += "]";
    
    return super.toString() + "msg.route: " + routeStr + "\n";
  }
}
