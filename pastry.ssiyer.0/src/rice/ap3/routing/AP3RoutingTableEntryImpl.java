package rice.ap3.routing;

import rice.pastry.NodeId;
import rice.ap3.messaging.*;

import java.util.Date;

/**
 * @(#) AP3RoutingTableEntryImpl.java
 *
 * Defines an entry in the routing table used by the AP3 system.
 *
 * @version $Id$
 * @author Gaurav Oberoi
 */
public class AP3RoutingTableEntryImpl implements AP3RoutingTableEntry {

  /**
   * The message id with which is entry is associated.
   * This value cannot be changed.
   */
  private AP3MessageID _id;

  /**
   * The timestamp on the entry in milliseconds
   */
  private long _timeStamp;

  /**
   * The node from which the message was received
   */
  private NodeId _source;

  /**
   * Constructor. Extracts all relevant information from
   * the given message.
   */
  AP3RoutingTableEntryImpl(AP3Message msg) {
    _id = msg.getID();
    _source = msg.getSource();
    this.stamp();
  }

  /**
   * Returns the message id related to this entry.
   */
  public AP3MessageID getID() {
    return _id;
  }

  /**
   * Returns the source of the message as a NodeId.
   */
  public NodeId getSource() {
    return _source;
  }

  /**
   * Returns the timestamp indicating the last time
   * the entry was updated.
   */
  public long getTimeStamp() {
    return _timeStamp;
  }

  /**
   * Stamps the entry with the current time. This is
   * the timestamp value returned by getTimeStamp()
   */
  public void stamp() {
    _timeStamp = new Date().getTime();
  }
}
