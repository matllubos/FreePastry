package rice.pastry.routing;

import rice.pastry.*;
import rice.pastry.messaging.*;
import rice.pastry.security.*;

import java.util.*;
import java.io.*;

/**
 * Broadcast message for a row from a routing table.
 * 
 * @version $Id$
 * 
 * @author Andrew Ladd
 */

public class BroadcastRouteRow extends Message implements Serializable {
  private NodeHandle fromNode;

  private RouteSet[] row;

  private static final Address addr = new RouteProtocolAddress();

  /**
   * Constructor.
   * 
   * @param cred the credentials
   * @param stamp the timestamp
   * @param from the node id
   * @param r the row
   */
  public BroadcastRouteRow(Credentials cred, Date stamp, NodeHandle from,
      RouteSet[] r) {
    super(addr, cred, stamp);

    fromNode = from;
    row = r;
    setPriority(0);
  }

  /**
   * Constructor.
   * 
   * @param stamp the timestamp
   * @param from the node id
   * @param r the row
   */
  public BroadcastRouteRow(Date stamp, NodeHandle from, RouteSet[] r) {
    super(addr, stamp);

    fromNode = from;
    row = r;
    setPriority(0);
  }

  /**
   * Constructor.
   * 
   * @param cred the credentials
   * @param from the node id
   * @param r the row
   */
  public BroadcastRouteRow(Credentials cred, NodeHandle from, RouteSet[] r) {
    super(addr, cred);

    fromNode = from;
    row = r;
    setPriority(0);
  }

  /**
   * Constructor.
   * 
   * @param from the node id
   * @param r the row
   */
  public BroadcastRouteRow(NodeHandle from, RouteSet[] r) {
    super(addr);

    fromNode = from;
    row = r;
    setPriority(0);
  }

  /**
   * Gets the from node.
   * 
   * @return the from node.
   */
  public NodeHandle from() {
    return fromNode;
  }

  /**
   * Gets the row that was sent in the message.
   * 
   * @return the row.
   */
  public RouteSet[] getRow() {
    return row;
  }

  public String toString() {
    String s = "";

    s += "BroadcastRouteRow(of " + fromNode.getNodeId() + ")";

    return s;
  }
}