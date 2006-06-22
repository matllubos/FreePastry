package rice.pastry.leafset;

import rice.p2p.commonapi.rawserialization.*;
import rice.pastry.*;
import rice.pastry.messaging.*;

import java.io.*;
import java.util.*;

/**
 * Request a leaf set from another node.
 * 
 * @version $Id$
 * 
 * @author Andrew Ladd
 */

public class RequestLeafSet extends PRawMessage implements Serializable {
  public static final short TYPE = 1;
  
  long timeStamp;
  
  /**
   * Constructor.
   * 
   * @param nh the return handle.
   */

  public RequestLeafSet(NodeHandle nh, long timeStamp) {
    this(null, nh, timeStamp);
  }

  /**
   * Constructor.
   * 
   * @param stamp the timestamp
   * @param nh the return handle
   */

  public RequestLeafSet(Date stamp, NodeHandle nh, long timeStamp) {
    super(LeafSetProtocolAddress.getCode(), stamp);
    setSender(nh);
    this.timeStamp = timeStamp;
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

  public String toString() {
    String s = "";

    s += "RequestLeafSet(by " + getSender().getNodeId() + ")";

    return s;
  }
  
  /***************** Raw Serialization ***************************************/  
  public short getType() {
    return TYPE;
  }

  public void serialize(OutputBuffer buf) throws IOException {
    buf.writeByte((byte)0); // version    
    buf.writeLong(timeStamp);
  }
  
  public RequestLeafSet(NodeHandle sender, InputBuffer buf) throws IOException {
    super(LeafSetProtocolAddress.getCode());
    
    setSender(sender);
    
    byte version = buf.readByte();
    switch(version) {
      case 0:
        timeStamp = buf.readLong();
        break;
      default:
        throw new IOException("Unknown Version: "+version);
    }
  }

  public long getTimeStamp() {
    return timeStamp;
  }  
}