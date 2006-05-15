package rice.pastry.join;

import rice.pastry.*;
import rice.pastry.messaging.*;

import java.io.*;
import java.util.*;

/**
 * Request for the join protocols on the local node to join the overlay.
 * 
 * @version $Id$
 * 
 * @author Andrew Ladd
 */

public class InitiateJoin extends Message implements Serializable {
  private NodeHandle[] handle;

  /**
   * Constructor.
   * 
   * @param nh the node handle that the join will begin from.
   */

  public InitiateJoin(NodeHandle nh) {
    this((NodeHandle[])null);
  	handle = new NodeHandle[1];
    handle[0] = nh;
  }


  public InitiateJoin(NodeHandle[] nh) {
    this(null, nh);
    handle = nh;
  }

  /**
   * Constructor.
   * 
   * @param jh a handle of the node trying to join the network.
   * @param stamp the timestamp
   * 
   * @param nh the node handle that the join will begin from.
   */

  public InitiateJoin(Date stamp, NodeHandle[] nh) {
    super(JoinAddress.getCode(), stamp);
    handle = nh;
  }

  /**
   * Gets the handle for the join.
   * 
   * Gets the first non-dead handle for the join.
   * 
   * @return the handle.
   */
  public NodeHandle getHandle() {
    for (int i = 0; i < handle.length; i++) {
      if (handle[i].isAlive()) return handle[i];
    }
    return null;
  }
}

