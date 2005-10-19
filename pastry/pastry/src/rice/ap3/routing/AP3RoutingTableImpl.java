package rice.ap3.routing;

import rice.pastry.NodeId;
import rice.ap3.messaging.*;

import java.util.Hashtable;

/**
 * @(#) AP3RoutingTableImpl.java
 *
 * The routing table used by the AP3 system.
 * It stores routing information for each
 * message based on its ID.
 *
 * @version $Id$
 * @author Gaurav Oberoi
 */
public class AP3RoutingTableImpl 
  implements AP3RoutingTable {

  /**
   * The routing table itself
   */
  private Hashtable _table;

  /**
   * Constructor
   */
  public AP3RoutingTableImpl() {
    _table = new Hashtable();
  }

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
  public void addEntry(AP3Message msg) throws MessageIDCollisionException {
    AP3RoutingTableEntry entry = this.getEntry(msg.getID());
    
    if(null != entry) {
      throw new MessageIDCollisionException("Collision ID: " + msg.getID());
    } else {
      entry = new AP3RoutingTableEntryImpl(msg);
      _table.put(entry.getID(), entry);
    }
  }

  /**
   * Drops the entry with the given id. Does nothing if it is
   * not in the table.
   *
   * @param id The AP3MessageID of the entry to drop.
   */
  public void dropEntry(AP3MessageID id) {
    _table.remove(id);
  }

  /**
   * Returns the entry with the given id. Null if the id
   * is not in the table.
   *
   * @param id The AP3MessageID of the entry to drop.
   */
  public AP3RoutingTableEntry getEntry(AP3MessageID id) {
    return (AP3RoutingTableEntry) _table.get(id);
  }

  /**
   * Returns the number of entries in the routing table.
   */
  public int getNumEntries() {
    return _table.size();
  }

  /**
   * Clears all entries in the routing table.
   */
  public void clear() {
    _table.clear();
  }
}






