
package rice.p2p.commonapi;

import java.io.*;

/**
 * @(#) IdRange.java
 *
 * Represents a contiguous range of Ids.
 * 
 * @version $Id$
 *
 * @author Alan Mislove
 * @author Peter Druschel
 */
public interface IdRange extends Serializable {
  
  /**
   * test if a given key lies within this range
   *
   * @param key the key
   * @return true if the key lies within this range, false otherwise
   */
  public boolean containsId(Id key);

  /**
   * get counterclockwise edge of range
   *
   * @return the id at the counterclockwise edge of the range (inclusive)
   */
  public Id getCCWId();

  /**
   * get clockwise edge of range
   *
   * @return the id at the clockwise edge of the range (exclusive)
   */
  public Id getCWId();

  /**
   * get the complement of this range
   *
   * @return This range's complement
   */
  public IdRange getComplementRange();
  
  /**
   * merges the given range with this range
   *
   * @return The merge
   */
  public IdRange mergeRange(IdRange range);
  
  /**
    * diffs the given range with this range
   *
   * @return The merge
   */
  public IdRange diffRange(IdRange range);
  
  /**
   * intersects the given range with this range
   *
   * @return The merge
   */
  public IdRange intersectRange(IdRange range);
  
  /**
   * returns whether or not this range is empty
   *
   * @return Whether or not this range is empty
   */
  public boolean isEmpty();
}



