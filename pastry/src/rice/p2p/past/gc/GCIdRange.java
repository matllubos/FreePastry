
package rice.p2p.past.gc;

import java.io.*;

import rice.p2p.commonapi.*;

/**
 * @(#) GCIdRange.java
 *
 * Represents a contiguous range of Ids with garbage collection times.
 * 
 * @version $Id$
 *
 * @author Alan Mislove
 */
public class GCIdRange implements IdRange {
  
  /**
   * The internal (normal) IdRange
   */
  protected IdRange range;
  
  /**
   * Constructor, which takes a normal IdRange
   *
   * @param range The normal range
   */
  public GCIdRange(IdRange range) {
    this.range = range;
    
    if (range instanceof GCIdRange) {
      throw new RuntimeException("SEVERE ERROR: Illegal creation of GCIdRange with GCIdRange!");
    }
  }
  
  /**
   * Returns the internal range
   *
   * @return The internal range
   */
  public IdRange getRange() {
    return range;
  }
  
  /**
   * test if a given key lies within this range
   *
   * @param key the key
   * @return true if the key lies within this range, false otherwise
   */
  public boolean containsId(Id key) {
    return range.containsId(((GCId) key).getId());
  }

  /**
   * get counterclockwise edge of range
   *
   * @return the id at the counterclockwise edge of the range (inclusive)
   */
  public Id getCCWId() {
    return new GCId(range.getCCWId(), GCPastImpl.DEFAULT_EXPIRATION);
  }
  
  /**
   * get clockwise edge of range
   *
   * @return the id at the clockwise edge of the range (exclusive)
   */
  public Id getCWId() {
    return new GCId(range.getCWId(), GCPastImpl.DEFAULT_EXPIRATION);
  }

  /**
   * get the complement of this range
   *
   * @return This range's complement
   */
  public IdRange getComplementRange() {
    return new GCIdRange(range.getComplementRange());
  }
  
  /**
   * merges the given range with this range
   *
   * @return The merge
   */
  public IdRange mergeRange(IdRange range) {
    return new GCIdRange(this.range.mergeRange(((GCIdRange) range).getRange()));
  }
  
  /**
   * diffs the given range with this range
   *
   * @return The merge
   */
  public IdRange diffRange(IdRange range) {
    return new GCIdRange(this.range.diffRange(((GCIdRange) range).getRange()));
  }
  
  /**
   * intersects the given range with this range
   *
   * @return The merge
   */
  public IdRange intersectRange(IdRange range) {
    return new GCIdRange(this.range.intersectRange(((GCIdRange) range).getRange()));
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
   * Returns a string
   *
   * @return THe string
   */
  public String toString() {
    return "{GC " + range + "}"; 
  }
}




