package rice.p2p.multiring;

import java.util.*;

import rice.p2p.commonapi.*;

/**
 * @(#) MutliringIdRange.java
 *
 * Represents a contiguous range of Ids in a multiring heirarchy.
 * 
 * @version $Id$
 *
 * @author Alan Mislove
 * @author Peter Druschel
 */
public class MultiringIdRange implements IdRange {
  
  /**
   * The actual IdRange
   */
  protected IdRange range;
  
  /**
   * The ringId of the nodes in the range
   */
  protected Id ringId;
  
  /**
   * Constructor
   */
  protected MultiringIdRange(Id ringId, IdRange range) {
    this.ringId = ringId;
    this.range = range;
    
    if ((ringId instanceof RingId) || (range instanceof MultiringIdRange))
      throw new IllegalArgumentException("Illegal creation of MRIdRange: " + ringId.getClass() + ", " + range.getClass());
  }
  
  /**
   * Returns the internal range
   *
   * @return The internal range
   */
  protected IdRange getRange() {
    return range;
  }
  
  /**
   * test if a given key lies within this range
   *
   * @param key the key
   * @return true if the key lies within this range, false otherwise
   */
  public boolean containsId(Id key) {
    if (key instanceof RingId) {
      RingId rkey = (RingId) key;
      if (!rkey.getRingId().equals(this.ringId)) {
        System.err.println("ERROR: Testing membership for keys in a different ring (got id " + key + "), range " + this);
        return false;
      }
      
      return range.containsId(rkey.getId());
    } else throw new IllegalArgumentException("Cannot test membership for keys other than RingId");
  }
  
  /**
  * get counterclockwise edge of range
   *
   * @return the id at the counterclockwise edge of the range (inclusive)
   */
  public Id getCCWId() {
    return RingId.build(ringId, range.getCCWId());
  }
  
  /**
    * get clockwise edge of range
   *
   * @return the id at the clockwise edge of the range (exclusive)
   */
  public Id getCWId() {
    return RingId.build(ringId, range.getCWId());
  }
  
  /**
    * get the complement of this range
   *
   * @return This range's complement
   */
  public IdRange getComplementRange() {
    return new MultiringIdRange(ringId, range.getComplementRange());
  }
  
  /**
    * merges the given range with this range
   *
   * @return The merge
   */
  public IdRange mergeRange(IdRange merge) {
    return new MultiringIdRange(ringId, range.mergeRange(((MultiringIdRange) merge).getRange()));
  }
  
  /**
    * diffs the given range with this range
   *
   * @return The merge
   */
  public IdRange diffRange(IdRange diff) {
    return new MultiringIdRange(ringId, range.diffRange(((MultiringIdRange) diff).getRange()));
  }
  
  /**
    * intersects the given range with this range
   *
   * @return The merge
   */
  public IdRange intersectRange(IdRange intersect) {
    return new MultiringIdRange(ringId, range.intersectRange(((MultiringIdRange) intersect).getRange()));
  }
  
  /**
    * returns whether or not this range is empty
   *
   * @return Whether or not this range is empty
   */
  public boolean isEmpty() {
    return range.isEmpty();
  }
  
  /**
   * Determines equality
   *
   * @param other To compare to
   * @return Equals
   */
  public boolean equals(Object o) {
    MultiringIdRange other = (MultiringIdRange) o;
    return (other.getRange().equals(range) && other.ringId.equals(ringId));
  }
  
  /**
   * Returns the hashCode
   *
   * @return hashCode
   */
  public int hashCode() {
    return (range.hashCode() + ringId.hashCode());
  }
  
  /**
   * Prints out the string
   *
   * @return A string
   */
  public String toString() {
    return "{RingId " + ringId + " " + range.toString() + "}";
  }
}



