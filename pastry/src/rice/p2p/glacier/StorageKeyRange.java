package rice.p2p.glacier;

import java.util.*;
import rice.p2p.commonapi.*;

public class StorageKeyRange implements IdRange {
  
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
  protected StorageKeyRange(Id ringId, IdRange range) {
    this.ringId = ringId;
    this.range = range;
  }
  
  /**
   * test if a given key lies within this range
   *
   * @param key the key
   * @return true if the key lies within this range, false otherwise
   */
  public boolean containsId(Id key) {
    System.err.println("StorageKeyRange.containsID() called");
    System.exit(1);
    return false;

//    return range.containsId(key);
  }
  
  /**
  * get counterclockwise edge of range
   *
   * @return the id at the counterclockwise edge of the range (inclusive)
   */
  public Id getCCWId() {
    System.err.println("StorageKeyRange.getCCWId() called");
    System.exit(1);
    return null;

//    return new RingId(ringId, range.getCCWId());
  }
  
  /**
    * get clockwise edge of range
   *
   * @return the id at the clockwise edge of the range (exclusive)
   */
  public Id getCWId() {
    System.err.println("StorageKeyRange.getCWId() called");
    System.exit(1);
    return null;

//    return new RingId(ringId, range.getCWId());
  }
  
  /**
    * get the complement of this range
   *
   * @return This range's complement
   */
  public IdRange getComplementRange() {
    System.err.println("StorageKeyRange.getComplementRange() called");
    System.exit(1);
    return null;

//    return new MultiringIdRange(ringId, range.getComplementRange());
  }
  
  /**
    * merges the given range with this range
   *
   * @return The merge
   */
  public IdRange mergeRange(IdRange merge) {
    System.err.println("StorageKeyRange.mergeRange() called");
    System.exit(1);
    return null;
//    return new MultiringIdRange(ringId, range.mergeRange(((MultiringIdRange) merge).getRange()));
  }
  
  /**
    * diffs the given range with this range
   *
   * @return The merge
   */
  public IdRange diffRange(IdRange diff) {
    System.err.println("StorageKeyRange.diffRange() called");
    System.exit(1);
    return null;

//    return new MultiringIdRange(ringId, range.diffRange(((MultiringIdRange) diff).getRange()));
  }
  
  /**
    * intersects the given range with this range
   *
   * @return The merge
   */
  public IdRange intersectRange(IdRange intersect) {
    System.err.println("StorageKeyRange.intersectRange() called");
    System.exit(1);
    return null;

//    return new MultiringIdRange(ringId, range.intersectRange(((MultiringIdRange) intersect).getRange()));
  }
  
  /**
    * returns whether or not this range is empty
   *
   * @return Whether or not this range is empty
   */
  public boolean isEmpty() {
    System.err.println("StorageKeyRange.isEmpty() called");
    System.exit(1);
    return false;

//    return range.isEmpty();
  }
  
  /**
   * Determines equality
   *
   * @param other To compare to
   * @return Equals
   */
  public boolean equals(Object o) {
    System.err.println("StorageKeyRange.equals() called");
    System.exit(1);
    return false;

//    MultiringIdRange other = (MultiringIdRange) o;
//    return (other.getRange().equals(range) && other.ringId.equals(ringId));
  }
  
  /**
   * Returns the hashCode
   *
   * @return hashCode
   */
  public int hashCode() {
    System.err.println("StorageKeyRange.hashCode() called");
    System.exit(1);
    return 0;

//    return (range.hashCode() + ringId.hashCode());
  }
  
  /**
   * Prints out the string
   *
   * @return A string
   */
  public String toString() {
    System.err.println("StorageKeyRange.toString() called");
    System.exit(1);
    return null;

//    return "{RingId " + ringId + " " + range.toString() + "}";
  }
}



