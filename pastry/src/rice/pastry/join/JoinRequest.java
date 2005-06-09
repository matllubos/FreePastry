package rice.pastry.join;

import rice.pastry.*;
import rice.pastry.messaging.*;
import rice.pastry.security.*;
import rice.pastry.routing.*;
import rice.pastry.leafset.*;

import java.io.*;
import java.util.*;

/**
 * Request to join this network.
 * 
 * @version $Id$
 * 
 * @author Andrew Ladd
 */

public class JoinRequest extends Message implements Serializable {
  private NodeHandle handle;

  private NodeHandle joinHandle;

  private int rowCount;

  private RouteSet rows[][];

  private LeafSet leafSet;

  /**
   * Constructor.
   * 
   * @param nh a handle of the node trying to join the network.
   */

  public JoinRequest(NodeHandle nh) {
    super(new JoinAddress());
    handle = nh;
    initialize();
    setPriority(0);
  }

  /**
   * Constructor.
   * 
   * @param nh a handle of the node trying to join the network.
   * @param stamp the timestamp
   */

  public JoinRequest(NodeHandle nh, Date stamp) {
    super(new JoinAddress(), stamp);
    handle = nh;
    initialize();
    setPriority(0);
  }

  /**
   * Constructor.
   * 
   * @param nh a handle of the node trying to join the network.
   * @param cred the credentials
   */

  public JoinRequest(NodeHandle nh, Credentials cred) {
    super(new JoinAddress(), cred);
    handle = nh;
    initialize();
    setPriority(0);
  }

  /**
   * Constructor.
   * 
   * @param nh a handle of the node trying to join the network.
   * @param cred the credentials
   * @param stamp the timestamp
   */

  public JoinRequest(NodeHandle nh, Credentials cred, Date stamp) {
    super(new JoinAddress(), cred, stamp);
    handle = nh;
    initialize();
    setPriority(0);
  }

  /**
   * Gets the handle of the node trying to join.
   * 
   * @return the handle.
   */

  public NodeHandle getHandle() {
    return handle;
  }

  /**
   * Gets the handle of the node that accepted the join request;
   * 
   * @return the handle.
   */

  public NodeHandle getJoinHandle() {
    return joinHandle;
  }

  /**
   * Gets the leafset of the node that accepted the join request;
   * 
   * @return the leafset.
   */

  public LeafSet getLeafSet() {
    return leafSet;
  }

  /**
   * Returns true if the request was accepted, false if it hasn't yet.
   */

  public boolean accepted() {
    return joinHandle != null;
  }

  /**
   * Accept join request.
   * 
   * @param nh the node handle that accepts the join request.
   */

  public void acceptJoin(NodeHandle nh, LeafSet ls) {
    joinHandle = nh;
    leafSet = ls;
  }

  /**
   * Returns the number of rows left to determine (in order).
   * 
   * @return the number of rows left.
   */

  public int lastRow() {
    return rowCount;
  }

  /**
   * Push row.
   * 
   * @param row the row to push.
   */

  public void pushRow(RouteSet row[]) {
    rows[--rowCount] = row;
  }

  /**
   * Get row.
   * 
   * @param i the row to get.
   * 
   * @return the row.
   */

  public RouteSet[] getRow(int i) {
    return rows[i];
  }

  /**
   * Get the number of rows.
   * 
   * @return the number of rows.
   */

  public int numRows() {
    return rows.length;
  }

  private void initialize() {
    joinHandle = null;

    rowCount = NodeId.nodeIdBitLength / RoutingTable.baseBitLength();

    rows = new RouteSet[rowCount][];
  }

  public String toString() {
    return "JoinRequest(" + (handle != null ? handle.getNodeId() : null) + ","
        + (joinHandle != null ? joinHandle.getNodeId() : null) + ")";
  }
}

