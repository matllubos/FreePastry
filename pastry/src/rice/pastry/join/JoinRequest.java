package rice.pastry.join;

import rice.p2p.commonapi.rawserialization.*;
import rice.pastry.*;
import rice.pastry.messaging.*;
import rice.pastry.routing.*;
import rice.pastry.leafset.*;

import java.io.*;
import java.util.*;

/**
 * Request to join this network.
 * 
 * 
 * 
 * 
 * @version $Id$
 * 
 * @author Jeff Hoye, Andrew Ladd
 */
public class JoinRequest extends PRawMessage {

  public static final byte HAS_HANDLE = 0x01;
  public static final byte HAS_JOIN_HANDLE = 0x02;
  public static final byte HAS_LEAFSET = 0x02;
  
  static final long serialVersionUID = 231671018732832563L;
  
  public static final short TYPE = 1;
  
  private NodeHandle handle;

  private NodeHandle joinHandle;

  private byte rowCount;

  private RouteSet rows[][];

  private LeafSet leafSet;

  private byte rtBaseBitLength;
  /**
   * Constructor.
   * 
   * @param nh a handle of the node trying to join the network.
   */
  public JoinRequest(NodeHandle nh, byte rtBaseBitLength) {
    this(nh, null, rtBaseBitLength);
  }

  /**
   * Constructor.
   * 
   * @param nh a handle of the node trying to join the network.
   * @param stamp the timestamp
   */
  public JoinRequest(NodeHandle nh, Date stamp, byte rtBaseBitLength) {
    super(JoinAddress.getCode(), stamp);
    handle = nh;
    initialize(rtBaseBitLength);
    setPriority(MAX_PRIORITY);
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

  private void initialize(byte rtBaseBitLength) {
    joinHandle = null;
    this.rtBaseBitLength = rtBaseBitLength;
    rowCount = (byte)(Id.IdBitLength / rtBaseBitLength);

    rows = new RouteSet[rowCount][];
  }

  public String toString() {
    return "JoinRequest(" + (handle != null ? handle.getNodeId() : null) + ","
        + (joinHandle != null ? joinHandle.getNodeId() : null) + ")";
  }

  /***************** Raw Serialization ***************************************/  
  public short getType() {
    return TYPE;
  }
  
  public void serialize(OutputBuffer buf) throws IOException {    
    buf.writeByte((byte)0); // version
    buf.writeByte((byte) rtBaseBitLength);    
    handle.serialize(buf);
    if (joinHandle != null) {
      buf.writeBoolean(true);
      joinHandle.serialize(buf);
    } else {
      buf.writeBoolean(false);
    }
    
    // encode the table
    buf.writeByte((byte) rowCount);
    int maxIndex = Id.IdBitLength / rtBaseBitLength;
    for (int i=0; i<maxIndex; i++) {
      RouteSet[] thisRow = rows[i];
      if (thisRow != null) {
        buf.writeBoolean(true);
        for (int j=0; j<thisRow.length; j++) {
          if (thisRow[j] != null) {
            buf.writeBoolean(true);
            thisRow[j].serialize(buf);
          } else {
            buf.writeBoolean(false);
          }
        }
      } else {
        buf.writeBoolean(false);
      }
    }
    
    if (leafSet != null) {
      buf.writeBoolean(true);
      leafSet.serialize(buf);
    } else {
      buf.writeBoolean(false);
    }
    
  }

  public JoinRequest(InputBuffer buf, NodeHandleFactory nhf, NodeHandle sender) throws IOException {
    super(JoinAddress.getCode());
    
    byte version = buf.readByte();
    switch(version) {
      case 0:
        setSender(sender);
        rtBaseBitLength = buf.readByte();
        initialize(rtBaseBitLength);
        
        handle = nhf.readNodeHandle(buf);
        if (buf.readBoolean())
          joinHandle = nhf.readNodeHandle(buf);
    
        rowCount = buf.readByte();
        int numRows = Id.IdBitLength / rtBaseBitLength;
        int numCols = 1 << rtBaseBitLength;
        for (int i=0; i<numRows; i++) {
          RouteSet[] thisRow;
          if (buf.readBoolean()) {
            thisRow = new RouteSet[numCols];
            for (int j=0; j<numCols; j++) {
              if (buf.readBoolean()) {
                thisRow[j] = new RouteSet(buf, nhf);
              } else {
                thisRow[j] = null;
              }
            }
          } else {
            thisRow = null;
          }
          rows[i] = thisRow;
        }
        
        if (buf.readBoolean())
          leafSet = LeafSet.build(buf, nhf);
        break;
      default:
        throw new IOException("Unknown Version: "+version);
    }
  }
}

