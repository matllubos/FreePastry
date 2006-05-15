package rice.p2p.multiring;

import java.io.IOException;
import java.util.*;

import rice.p2p.commonapi.*;
import rice.p2p.commonapi.rawserialization.*;

/**
* @(#) MultiringNodeHandleSet.java
 *
 * An implementation of a NodeHandleSet for use with multiple rings
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */
public class MultiringNodeHandleSet implements NodeHandleSet {
  public static final short TYPE = 10;

  /**
   * The actual node handle set
   */
  protected NodeHandleSet set;
  
  /**
   * The handle's ringId
   */
  protected Id ringId;
  
  /**
   * Constructor 
   */
  protected MultiringNodeHandleSet(Id ringId, NodeHandleSet set) {
    this.ringId = ringId;
    this.set = set;
    
    if ((ringId instanceof RingId) || (set instanceof MultiringNodeHandleSet))
      throw new IllegalArgumentException("Illegal creation of MRNodeHandleSet: " + ringId.getClass() + ", " + set.getClass());
  }
  
  /**
   * Returns the internal set
   *
   * @return The internal set
   */
  protected NodeHandleSet getSet() {
    return set;
  }
  
  /**
   * Puts a NodeHandle into the set.
   *
   * @param handle the handle to put.
   *
   * @return true if the put succeeded, false otherwise.
   */
  public boolean putHandle(NodeHandle handle) {
    return set.putHandle(((MultiringNodeHandle) handle).getHandle());
  }
  
  /**
   * Finds the NodeHandle associated with the NodeId.
   *
   * @param id a node id.
   * @return the handle associated with that id or null if no such handle is found.
   */
  public NodeHandle getHandle(Id id) {
    NodeHandle handle = set.getHandle(((RingId) id).getId());
    
    if (handle != null)
      return new MultiringNodeHandle(ringId, handle);
    else
      return null;
  }
  
  /**
   * Gets the ith element in the set.
   *
   * @param i an index.
   * @return the handle associated with that id or null if no such handle is found.
   */
  public NodeHandle getHandle(int i) {
    NodeHandle handle = set.getHandle(i);
    
    if (handle != null)
      return new MultiringNodeHandle(ringId, handle);
    else
      return null;
  }
  
  /**
    * Verifies if the set contains this particular id.
   *
   * @param id a node id.
   * @return true if that node id is in the set, false otherwise.
   */
  public boolean memberHandle(Id id) {
    return set.memberHandle(((RingId) id).getId());
  }
  
  /**
    * Removes a node id and its handle from the set.
   *
   * @param nid the node to remove.
   *
   * @return the node handle removed or null if nothing.
   */
  public NodeHandle removeHandle(Id id) {
    NodeHandle handle = set.removeHandle(((RingId) id).getId());
    
    if (handle != null)
      return new MultiringNodeHandle(ringId, handle);
    else
      return null;
  }
  
  /**
    * Gets the size of the set.
   *
   * @return the size.
   */
  public int size() {
    return set.size();
  }
  
  /**
    * Gets the index of the element with the given node id.
   *
   * @param id the id.
   *
   * @return the index or throws a NoSuchElementException.
   */
  public int getIndexHandle(Id id) throws NoSuchElementException {
    return set.getIndexHandle(((RingId) id).getId());
  }
  
  /**
   * Determines equality
   *
   * @param other To compare to
   * @return Equals
   */
  public boolean equals(Object o) {
    MultiringNodeHandleSet other = (MultiringNodeHandleSet) o;
    return (other.getSet().equals(set) && other.ringId.equals(ringId));
  }
  
  /**
   * Returns the hashCode
   *
   * @return hashCode
   */
  public int hashCode() {
    return (set.hashCode() + ringId.hashCode());
  }
  
  /**
   * Prints out the string
   *
   * @return A string
   */
  public String toString() {
    return "{RingId " + ringId + " " + set.toString() + "}";
  }

  public MultiringNodeHandleSet(InputBuffer buf, Endpoint endpoint) throws IOException {
    ringId = endpoint.readId(buf, buf.readShort());
    short type = buf.readShort();
    set = endpoint.readNodeHandleSet(buf, type);
  }
  
  public void serialize(OutputBuffer buf) throws IOException {
    buf.writeShort(ringId.getType());
    ringId.serialize(buf);
    buf.writeShort(set.getType());
    set.serialize(buf);
  }
  
  public short getType() {
    return TYPE; 
  }
}
