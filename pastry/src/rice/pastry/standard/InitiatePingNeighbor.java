package rice.pastry.standard;

import java.io.Serializable;

import rice.pastry.leafset.LeafSetProtocolAddress;
import rice.pastry.messaging.Message;

/**
 * Initiate leaf set maintenance on the local node.
 * 
 * @version $Id$
 * 
 * @author Peter Druschel
 */

public class InitiatePingNeighbor extends Message implements Serializable {

  /**
   * Constructor.
   *  
   */

  public InitiatePingNeighbor() {
    super(new LeafSetProtocolAddress());
  }

}