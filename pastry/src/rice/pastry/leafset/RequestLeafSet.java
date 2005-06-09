package rice.pastry.leafset;

import rice.pastry.*;
import rice.pastry.messaging.*;
import rice.pastry.security.*;

import java.io.*;
import java.util.*;

/**
 * Request a leaf set from another node.
 * 
 * @version $Id$
 * 
 * @author Andrew Ladd
 */

public class RequestLeafSet extends Message implements Serializable {
  private NodeHandle handle;

  /**
   * Constructor.
   * 
   * @param nh the return handle.
   */

  public RequestLeafSet(NodeHandle nh) {
    super(new LeafSetProtocolAddress());
    handle = nh;
    setPriority(0);
  }

  /**
   * Constructor.
   * 
   * @param cred the credentials.
   * @param nh the return handle.
   */

  public RequestLeafSet(Credentials cred, NodeHandle nh) {
    super(new LeafSetProtocolAddress(), cred);
    handle = nh;
    setPriority(0);
  }

  /**
   * Constructor.
   * 
   * @param stamp the timestamp
   * @param nh the return handle
   */

  public RequestLeafSet(Date stamp, NodeHandle nh) {
    super(new LeafSetProtocolAddress(), stamp);
    handle = nh;
    setPriority(0);
  }

  /**
   * Constructor.
   * 
   * @param cred the credentials.
   * @param stamp the timestamp
   * @param nh the return handle.
   */

  public RequestLeafSet(Credentials cred, Date stamp, NodeHandle nh) {
    super(new LeafSetProtocolAddress(), cred, stamp);
    handle = nh;
    setPriority(0);
  }

  /**
   * The return handle for the message
   * 
   * @return the node handle
   */

  public NodeHandle returnHandle() {
    return handle;
  }

  public String toString() {
    String s = "";

    s += "RequestLeafSet(by " + handle.getNodeId() + ")";

    return s;
  }
}