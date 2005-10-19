package rice.ap3.routing;

import rice.pastry.NodeHandle;
import rice.ap3.messaging.*;

/**
 * @(#) AP3RoutingTableEntry.java
 *
 * Defines an entry in the routing table used by the AP3 system.
 *
 * @version $Id$
 * @author Gaurav Oberoi
 */
public interface AP3RoutingTableEntry {

  /**
   * Returns the message id related to this entry.
   */
  public AP3MessageID getID();

  /**
   * Returns the source of the message as a NodeId.
   */
  public NodeHandle getSource();

  /**
   * Returns the timestamp indicating the last time
   * the entry was updated.
   */
  public long getTimeStamp();

  /**
   * Stamps the entry with the current time. This is
   * the timestamp value returned by getTimeStamp()
   */
  public void stamp();
}




