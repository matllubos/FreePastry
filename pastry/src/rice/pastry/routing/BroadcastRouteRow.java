package rice.pastry.routing;

import rice.p2p.commonapi.rawserialization.*;
import rice.pastry.*;
import rice.pastry.messaging.*;

import java.util.*;
import java.io.*;

/**
 * Broadcast message for a row from a routing table.
 * 
 * @version $Id$
 * 
 * @author Andrew Ladd
 */

public class BroadcastRouteRow extends PRawMessage implements Serializable {

  
  private NodeHandle fromNode;

  private RouteSet[] row;

  public static final short TYPE = 2;

  
  
  /**
   * Constructor.
   * 
   * @param stamp the timestamp
   * @param from the node id
   * @param r the row
   */
  public BroadcastRouteRow(Date stamp, NodeHandle from, RouteSet[] r) {
    super(RouteProtocolAddress.getCode(), stamp);
    fromNode = from;
    row = r;
    setPriority(MAX_PRIORITY);
  }

  /**
   * Constructor.
   * 
   * @param from the node id
   * @param r the row
   */
  public BroadcastRouteRow(NodeHandle from, RouteSet[] r) {
    this(null, from, r);
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
  
  /***************** Raw Serialization ***************************************/
  public short getType() {
    return TYPE;
  }
  
  public void serialize(OutputBuffer buf) throws IOException {
    buf.writeByte((byte)0); // version
    fromNode.serialize(buf);
    buf.writeByte((byte) row.length);
    for (int i=0; i<row.length; i++) {
      if (row[i] != null) {
        buf.writeBoolean(true);
        row[i].serialize(buf);
      } else {
        buf.writeBoolean(false);
      }
    }
  }

  public BroadcastRouteRow(InputBuffer buf, NodeHandleFactory nhf) throws IOException {
    super(RouteProtocolAddress.getCode(), null);    
    
    byte version = buf.readByte();
    switch(version) {
      case 0:
        fromNode = nhf.readNodeHandle(buf);
        row = new RouteSet[buf.readByte()];
        for (int i=0; i<row.length; i++)
          if (buf.readBoolean()) {
            row[i] = new RouteSet(buf, nhf);
          }
        break;
      default:
        throw new IOException("Unknown Version: "+version);
    }      
  }
}