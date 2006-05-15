package rice.pastry.routing;

import rice.p2p.commonapi.rawserialization.*;
import rice.pastry.*;
import rice.pastry.messaging.*;

import java.io.*;
import java.util.*;

/**
 * Request a row from the routing table from another node.
 * 
 * @version $Id$
 * 
 * @author Andrew Ladd
 */

public class RequestRouteRow extends PRawMessage implements Serializable {
  public static final short TYPE = 1;

  private byte row;

  /**
   * Constructor.
   * 
   * @param nh the return handle.
   * @param r which row
   */

  public RequestRouteRow(NodeHandle nh, byte r) {
    this(null, nh, r);
  }

  /**
   * Constructor.
   * 
   * @param stamp the timestamp
   * @param nh the return handle
   * @param r which row
   */
  public RequestRouteRow(Date stamp, NodeHandle nh, byte r) {
    super(RouteProtocolAddress.getCode(), stamp);
    setSender(nh);
    row = r;
    setPriority(MAX_PRIORITY);
  }

  /**
   * The return handle for the message
   * 
   * @return the node handle
   */

  public NodeHandle returnHandle() {
    return getSender();
  }

  /**
   * Gets the row that made the request.
   * 
   * @return the row.
   */

  public int getRow() {
    return row;
  }

  public String toString() {
    String s = "";

    s += "RequestRouteRow(row " + row + " by " + getSender().getNodeId() + ")";

    return s;
  }
  
  /***************** Raw Serialization ***************************************/  
  public short getType() {
    return TYPE;
  }
  
  public void serialize(OutputBuffer buf) throws IOException {
    buf.writeByte((byte)0); // version    
    buf.writeByte(row);
  }
  
  public RequestRouteRow(NodeHandle sender, InputBuffer buf) throws IOException {
    super(RouteProtocolAddress.getCode(), null);
    setSender(sender);
    
    byte version = buf.readByte();
    switch(version) {
      case 0:
        row = buf.readByte();
        setPriority(MAX_PRIORITY);
        break;
      default:
        throw new IOException("Unknown Version: "+version);
    }
  }
}