package rice.ap3.routing;

import rice.pastry.NodeId;
import rice.ap3.messaging.*;

/**
 * @(#) AP3RoutingTable.java
 *
 * Defines the routing table used by the AP3 system to
 * store routing information for each
 * message based on its ID.
 *
 * @version $Id$
 * @author Gaurav Oberoi
 */
public interface AP3RoutingTable {

  /**
   * Adds an entry to the routing table. Extracts
   * all relevant information from the AP3Message.
   *
   * <p>
   * If an entry with the same id as the given message
   * is already in the routing table, an exception is thrown.
   *
   * @param msg The AP3Message which we are creating this entry for.
   */
  public void addEntry(AP3Message msg) throws MessageIDCollisionException;

  /**
   * Drops the entry with the given id. Does nothing if it is
   * not in the table.
   *
   * @param id The AP3MessageID of the entry to drop.
   */
  public void dropEntry(AP3MessageID id);

  /**
   * Returns the entry with the given id, null if the id
   * is not in the table.
   *
   * @param id The AP3MessageID of the entry to drop.
   */
  public AP3RoutingTableEntry getEntry(AP3MessageID id);
}
