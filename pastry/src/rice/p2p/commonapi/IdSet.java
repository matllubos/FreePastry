
package rice.p2p.commonapi;

import java.io.Serializable;
import java.util.*;

/**
 * @(#) IdSet.java
 *
 * Represents a set of ids.
 * 
 * @version $Id$
 *
 * @author Alan Mislove
 * @author Peter Druschel
 */
public interface IdSet extends Serializable {

  /**
   * return the number of elements
   */
  public int numElements();

  /**
   * add a member
   * @param id the id to add
   */
  public void addId(Id id);

  /**
   * remove a member
   * @param id the id to remove
   */
  public void removeId(Id id);

  /**
   * test membership
   * @param id the id to test
   * @return true of id is a member, false otherwise
   */
  public boolean isMemberId(Id id);

  /**
   * return a subset of this set, consisting of the member ids in a given range
   * @param from the lower end of the range (inclusive)
   * @param to the upper end of the range (exclusive)
   * @return the subset
   */
  public IdSet subSet(IdRange range);

  /**
   * return an iterator over the elements of this set
   * @return the interator
   */
  public Iterator getIterator();
  
  /**
   * return this set as an array
   * @return the array
   */
  public Id[] asArray();
  
  /**
   * return a hash of this set
   *
   * @return the hash of this set
   */
  public byte[] hash();
  
  /**
   * Override clone() to make it publicly accessible
   *
   * @return A clone of this set
   */
  public Object clone();
  
  /**
   * Returns a new, empty IdSet of this type
   *
   * @return A new IdSet
   */
  public IdSet build();
}
