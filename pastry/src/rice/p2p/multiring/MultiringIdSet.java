package rice.p2p.multiring;

import java.util.*;

import rice.p2p.commonapi.*;

/**
 * @(#) MultringIdSet.java
 *
 * Represents a set of ids in a multiring heirarchy
 * 
 * @version $Id$
 *
 * @author Alan Mislove
 * @author Peter Druschel
 */
public class MultiringIdSet implements IdSet {
  
  /**
   * Serialver for backwards compatibility
   */
  static final long serialVersionUID = -7675959536005571206L;
  
  /**
   * The actual IdSet
   */
  protected IdSet set;
  
  /**
  * The ringId of the ids in the set
   */
  protected Id ringId;
  
  /**
   * Constructor
   */
  protected MultiringIdSet(Id ringId, IdSet set) {
    this.ringId = ringId;
    this.set = set;
    
    if ((ringId instanceof RingId) || (set instanceof MultiringIdSet))
      throw new IllegalArgumentException("Illegal creation of MRIdSet: " + ringId.getClass() + ", " + set.getClass());
  }
  
  /**
   * Returns the internal set
   *
   * @return The internal set
   */
  protected IdSet getSet() {
    return set;
  }
  
  /**
   * return the number of elements
   */
  public int numElements() {
    return set.numElements();
  }
  
  /**
   * add a member
   * @param id the id to add
   */
  public void addId(Id id) {
    set.addId(((RingId) id).getId());
  }
  
  /**
    * remove a member
   * @param id the id to remove
   */
  public void removeId(Id id) {
    set.removeId(((RingId) id).getId());
  }
  
  /**
    * test membership
   * @param id the id to test
   * @return true of id is a member, false otherwise
   */
  public boolean isMemberId(Id id) {
    return set.isMemberId(((RingId) id).getId());
  }
  
  /**
   * return a subset of this set, consisting of the member ids in a given range
   * @param from the lower end of the range (inclusive)
   * @param to the upper end of the range (exclusive)
   * @return the subset
   */
  public IdSet subSet(IdRange range) {
    if (range == null)
      return (IdSet) this.clone();
    else
      return new MultiringIdSet(ringId, set.subSet(((MultiringIdRange) range).getRange()));
  }
  
  /**
   * return an iterator over the elements of this set
   * @return the interator
   */
  public Iterator getIterator() {
    return new Iterator() {
      protected Iterator i = set.getIterator();
      
      public boolean hasNext() {
        return i.hasNext();
      }
      
      public Object next() {
        return RingId.build(ringId, (Id) i.next());
      }
      
      public void remove() {
        i.remove();
      }
    };
  }
  
  /**
   * return this set as an array
   * @return the array
   */
  public Id[] asArray() {
    Id[] result = set.asArray();
    
    for (int i=0; i<result.length; i++)
      result[i] = RingId.build(ringId, result[i]);
    
    return result;
  }
  
  /**
   * return a hash of this set
   *
   * @return the hash of this set
   */
  public byte[] hash() {
    return set.hash();
  }
  
  /**
   * Determines equality
   *
   * @param other To compare to
   * @return Equals
   */
  public boolean equals(Object o) {
    MultiringIdSet other = (MultiringIdSet) o;
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
  
  /**
   * Clones this object
   *
   * @return a clone
   */
  public Object clone() {
    return new MultiringIdSet(ringId, (IdSet) set.clone());
  }
  
  /**
   * Returns a new, empty IdSet of this type
   *
   * @return A new IdSet
   */
  public IdSet build() {
    return new MultiringIdSet(ringId, set.build());
  }
}
