package rice.p2p.glacier;

import java.util.*;
import rice.p2p.commonapi.*;

/**
 * DESCRIBE THE CLASS
 *
 * @version $Id$
 * @author ahae
 */
public class VersionKeyRange implements IdRange {

  /**
   * The actual IdRange
   */
  protected IdRange range;

  /**
   * Constructor
   *
   * @param ringId DESCRIBE THE PARAMETER
   * @param range DESCRIBE THE PARAMETER
   */
  protected VersionKeyRange(IdRange range) {
    this.range = range;
  }

  /**
   * get counterclockwise edge of range
   *
   * @return the id at the counterclockwise edge of the range (inclusive)
   */
  public Id getCCWId() {
    return new VersionKey(range.getCCWId(), 0L);
  }

  /**
   * get clockwise edge of range
   *
   * @return the id at the clockwise edge of the range (exclusive)
   */
  public Id getCWId() {
    return new VersionKey(range.getCWId(), 0L);
  }

  /**
   * get the complement of this range
   *
   * @return This range's complement
   */
  public IdRange getComplementRange() {
    System.err.println("VersionKeyRange.getComplementRange() called");
    System.exit(1);
    return null;
//    return new MultiringIdRange(ringId, range.getComplementRange());
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
   * test if a given key lies within this range
   *
   * @param key the key
   * @return true if the key lies within this range, false otherwise
   */
  public boolean containsId(Id key) {
    return range.containsId(((VersionKey)key).id);
  }

  /**
   * merges the given range with this range
   *
   * @param merge DESCRIBE THE PARAMETER
   * @return The merge
   */
  public IdRange mergeRange(IdRange merge) {
    System.err.println("VersionKeyRange.mergeRange() called");
    System.exit(1);
    return null;
//    return new MultiringIdRange(ringId, range.mergeRange(((MultiringIdRange) merge).getRange()));
  }

  /**
   * diffs the given range with this range
   *
   * @param diff DESCRIBE THE PARAMETER
   * @return The merge
   */
  public IdRange diffRange(IdRange diff) {
    System.err.println("VersionKeyRange.diffRange() called");
    System.exit(1);
    return null;
//    return new MultiringIdRange(ringId, range.diffRange(((MultiringIdRange) diff).getRange()));
  }

  /**
   * intersects the given range with this range
   *
   * @param intersect DESCRIBE THE PARAMETER
   * @return The merge
   */
  public IdRange intersectRange(IdRange intersect) {
    System.err.println("VersionKeyRange.intersectRange() called");
    System.exit(1);
    return null;
//    return new MultiringIdRange(ringId, range.intersectRange(((MultiringIdRange) intersect).getRange()));
  }

  /**
   * Determines equality
   *
   * @param o DESCRIBE THE PARAMETER
   * @return Equals
   */
  public boolean equals(Object o) {
    return ((VersionKeyRange)o).range.equals(range);
  }

  /**
   * Returns the hashCode
   *
   * @return hashCode
   */
  public int hashCode() {
    return range.hashCode();
  }

  /**
   * Prints out the string
   *
   * @return A string
   */
  public String toString() {
    return "[VKRange " + range + "]";
  }
}


