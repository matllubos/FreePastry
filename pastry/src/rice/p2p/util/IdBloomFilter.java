
package rice.p2p.util;

import java.io.*;
import java.math.*;
import java.util.*;

import rice.p2p.commonapi.*;

/**
 * @(#) IdBloomFilter.java
 *
 * Class which is an implementation of a bloom filter which takes Ids as elements.  
 * This class simply wraps a normal BloomFilter, but provides convienent methods
 * for constructing and checking for the existence of Ids.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */
public class IdBloomFilter implements Serializable {
  
  // serialver for backwards compatibility
  private static final long serialVersionUID = -9122948172786936161L;
  
  // note cannot configure these because Serializable
  /**
   * The number of bits per key in bloom filters
   */
  public static int NUM_BITS_PER_KEY = 4;

  /**
   * The number of different hash functions to use in bloom filters
   */
  public static int NUM_HASH_FUNCTIONS = 2;
  
  /**
   * An internal byte[] for managing ids in a memory-efficent manner
   */
  protected transient byte[] array;
  
  /**
   * The parameters to the hash functions for this bloom filter
   */
  protected BloomFilter filter;
    
  /**
    * Constructor which takes the number of hash functions to use
   * and the length of the set to use.
   *
   * @param num The number of hash functions to use
   * @param length The length of the underlying bit set
   */
  public IdBloomFilter(IdSet set) {
    this.filter = new BloomFilter(NUM_HASH_FUNCTIONS, NUM_BITS_PER_KEY * set.numElements());
    Iterator i = set.getIterator();  
    
    while (i.hasNext())
      addId((Id) i.next());
  }
  
  /**
   * Internal method for checking to see if the array exists, and if not,
   * instanciating it.  It also places the given Id into the array.
   *
   * @param id An id to build the array from
   */
  protected void checkArray(Id id) {
    if (array == null) 
      array = id.toByteArray();
    else
      id.toByteArray(array, 0);
  }
  
  /**
   * Method which adds an Id to the underlying bloom filter
   *
   * @param id The id to add
   */
  protected void addId(Id id) {
    checkArray(id);
    filter.add(array);
  }
  
  /**
   * Method which returns whether or not an Id *may* be in the set.  Specifically, 
   * if this method returns false, the element is definately not in the set.  Otherwise, 
   * if true is returned, the element may be in the set, but it is not guaranteed.
   *
   * @param id The id to check for
   */
  public boolean check(Id id) {
    checkArray(id);
    return filter.check(array);
  }
  
  /**
   * Method which checks an entire IdSet to see if they exist in this bloom filter, and
   * returns the response by adding elements to the other provided id set.
   *
   * @param set THe set to check for
   * @param result The set to put the non-existing objects into
   * @param max The maximum number of keys to return
   */
  public void check(IdSet set, IdSet result, int max) {
    Iterator it = set.getIterator();
    int count = 0;
    
    while (it.hasNext() && (count < max)) {
      Id next = (Id) it.next();
      
      if (! check(next)) {
        result.addId(next);
        count++;
      }
    }
  }
}
