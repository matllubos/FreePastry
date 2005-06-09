package rice.pastry.join;

import rice.pastry.*;
import rice.pastry.messaging.*;
import rice.pastry.security.*;

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
  private NodeHandle handle;

  /**
   * Constructor.
   * 
   * @param nh the node handle that the join will begin from.
   */

  public InitiateJoin(NodeHandle nh) {
    super(new JoinAddress());

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

  public InitiateJoin(Date stamp, NodeHandle nh) {
    super(new JoinAddress(), stamp);

    handle = nh;
  }

  /**
   * Constructor.
   * 
   * @param jh a handle of the node trying to join the network.
   * @param cred the credentials
   * 
   * @param nh the node handle that the join will begin from.
   */

  public InitiateJoin(Credentials cred, NodeHandle nh) {
    super(new JoinAddress(), cred);

    handle = nh;
  }

  /**
   * Constructor.
   * 
   * @param jh a handle of the node trying to join the network.
   * @param cred the credentials
   * @param stamp the timestamp
   * 
   * @param nh the node handle that the join will begin from.
   */

  public InitiateJoin(Credentials cred, Date stamp, NodeHandle nh) {
    super(new JoinAddress(), cred, stamp);

    handle = nh;
  }

  /**
   * Gets the handle for the join.
   * 
   * @return the handle.
   */

  public NodeHandle getHandle() {
    return handle;
  }
}

