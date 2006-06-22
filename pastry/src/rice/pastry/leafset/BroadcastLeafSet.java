package rice.pastry.leafset;

import rice.p2p.commonapi.rawserialization.*;
import rice.pastry.*;
import rice.pastry.messaging.*;

import java.io.*;
import java.util.*;

/**
 * Broadcast a leaf set to another node.
 * 
 * @version $Id$
 * 
 * @author Andrew Ladd
 */

public class BroadcastLeafSet extends PRawMessage {
  public static final short TYPE = 2;
  
  public static final int Update = 0;

  public static final int JoinInitial = 1;

  public static final int JoinAdvertise = 2;

  public static final int Correction = 3;

  private NodeHandle fromNode;

  private LeafSet theLeafSet;

  private int theType;

  private long requestTimeStamp;
  
  /**
   * Constructor.
   */

  public BroadcastLeafSet(NodeHandle from, LeafSet leafSet, int type, long requestTimeStamp) {
    this(null, from, leafSet, type, requestTimeStamp);
  }

  /**
   * Constructor.
   * 
   * @param stamp the timestamp
   */

  public BroadcastLeafSet(Date stamp, NodeHandle from, LeafSet leafSet, int type, long requestTimeStamp) {
    super(LeafSetProtocolAddress.getCode(), stamp);

    fromNode = from;
    theLeafSet = leafSet;
    theType = type;
    this.requestTimeStamp = requestTimeStamp;
    setPriority(MAX_PRIORITY);
  }

  /**
   * Returns the node id of the node that broadcast its leaf set.
   * 
   * @return the node id.
   */

  public NodeHandle from() {
    return fromNode;
  }

  /**
   * Returns the leaf set that was broadcast.
   * 
   * @return the leaf set.
   */

  public LeafSet leafSet() {
    return theLeafSet;
  }

  /**
   * Returns the type of leaf set.
   * 
   * @return the type.
   */

  public int type() {
    return theType;
  }

  public String toString() {
    String s = "BroadcastLeafSet(of " + fromNode.getNodeId() + ":" + theLeafSet + ")";
    return s;
  }
  
  /***************** Raw Serialization ***************************************/  
  public short getType() {
    return TYPE; 
  }
  
  public void serialize(OutputBuffer buf) throws IOException {
    buf.writeByte((byte)0); // version
    fromNode.serialize(buf);
    theLeafSet.serialize(buf);
    buf.writeByte((byte) theType);
    buf.writeLong(requestTimeStamp);
  }
  
  public BroadcastLeafSet(InputBuffer buf, NodeHandleFactory nhf) throws IOException {
    super(LeafSetProtocolAddress.getCode());
    
    byte version = buf.readByte();
    switch(version) {
      case 0:
        fromNode = nhf.readNodeHandle(buf);
        theLeafSet = LeafSet.build(buf, nhf);
        theType = buf.readByte();
        requestTimeStamp = buf.readLong();
        break;
      default:
        throw new IOException("Unknown Version: "+version);
    }
  }

  public long getTimeStamp() {
    return requestTimeStamp;
  }  
}