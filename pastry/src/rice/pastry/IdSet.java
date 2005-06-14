
package rice.pastry;

import java.io.Serializable;
import java.util.*;
import java.security.*;
import rice.p2p.util.*;

/**
 * Represents a set of Pastry ids.
 * *
 * @version $Id$
 *
 * @author Peter Druschel
 */

public class IdSet implements rice.p2p.commonapi.IdSet {
  
  
  static final long serialVersionUID = -1565571743719309172L;

  private SortedMap idSet;

  // a cache of the fingerprint hash
  private byte[] cachedHash;
  private boolean validHash;

  /**
   * Constructor.
   */
  public IdSet() {
    idSet = new RedBlackMap();
    validHash = false;
  }

  /**
   * Constructor.
   * constructs 
   * @param s the TreeSet based on which we construct a new IdSet
   */
  public IdSet(SortedMap s) {
    idSet = s;
    validHash = false;
  }

  /**
   * Copy constructor.
   * constructs a shallow copy of the given IdSet o.
   * @param o the IdSet to copy
   */
//  public IdSet(IdSet o) {
//    idSet = o.idSet;
//    cachedHash = o.cachedHash;
//    validHash = o.validHash;
//  }

  /**
   * return the number of elements
   */
  public int numElements() {
    return idSet.size();
  }

  /**
   * add a member
   * @param id the id to add
   */
  public void addMember(Id id) {
    idSet.put(id, null);
    validHash = false;
  }

  /**
   * remove a member
   * @param id the id to remove
   */
  public void removeMember(Id id) {
    idSet.remove(id);
    validHash = false;
  }

  /**
   * test membership
   * @param id the id to test
   * @return true of id is a member, false otherwise
   */
  public boolean isMember(Id id) {
    return idSet.containsKey(id);
  }

  /**
   * return the smallest member id
   * @return the smallest id in the set
   */
  public Id minMember() {
    return (Id) idSet.firstKey();
  }

  /**
   * return the largest member id
   * @return the largest id in the set
   */
  public Id maxMember() {
    return (Id) idSet.lastKey();
  }

  /**
   * return a subset of this set, consisting of the member ids in a given range
   * @param from the counterclockwise end of the range (inclusive)
   * @param to the clockwise end of the range (exclusive)
   * @return the subset
   */
  public IdSet subSet(Id from, Id to) {
    return new IdSet(idSet.subMap(from, to));
/*    IdSet res;

    if (from.compareTo(to) <= 0) {
      res = new IdSet(idSet.subMap(from, to));
    } else {
      res = new IdSet(idSet.tailMap(from));
      //SortedSet ss = idSet.tailSet(from);
      //ss.addAll(idSet.headSet(to));
      //res = new IdSet( (TreeSet) ss);
      res.idSet.putAll(idSet.headMap(to));
    }

    return res; */
  }

  /**
   * return a subset of this set, consisting of the member ids in a given range
   * @param range the range
   * @return the subset
   */
  public IdSet subSet(IdRange range) {
    if (range.isEmpty()) {
      return new IdSet();
    } else if (range.getCCW().equals(range.getCW())) {
      return this;
    } else {
      return subSet(range.getCCW(), range.getCW());
    }
  }

  /**
   * return an iterator over the elements of this set
   * @return the interator
   */
  public Iterator getIterator() {
    return idSet.keySet().iterator();
  }

  /**
   * compute a fingerprint of the members in this IdSet
   *
   * @return an Id containing the secure hash of this set
   */
  public byte[] getHash() {
    if (validHash) return cachedHash;

    // recompute the hash
    MessageDigest md = null;
    try {
      md = MessageDigest.getInstance("SHA");
    } catch ( NoSuchAlgorithmException e ) {
      throw new RuntimeException("No SHA support!",e);
      //return null;
    }

    Iterator it = getIterator();
    byte[] raw = new byte[Id.IdBitLength / 8];
    Id id;

    while (it.hasNext()) {
      id = (Id) it.next();
      id.blit(raw);
      md.update(raw);
    }

    cachedHash = md.digest();
    validHash = true;

    return cachedHash;
  }

  /**
   * Returns a string representation of the IdSet.
   */
  public String toString() {
    Iterator it = getIterator();
    Id key;
    String s = "[ IdSet: ";
    while(it.hasNext()) {
      key = (Id)it.next();
      s = s + key + ",";

    }
    s = s + " ]";
    return s;
  }

  // Common API Support

  /**
   * add a member
   * @param id the id to add
   */
  public void addId(rice.p2p.commonapi.Id id) {
    addMember((Id) id);
  }

  /**
   * remove a member
   * @param id the id to remove
   */
  public void removeId(rice.p2p.commonapi.Id id) {
    removeMember((Id) id);
  }

  /**
   * test membership
   * @param id the id to test
   * @return true of id is a member, false otherwise
   */
  public boolean isMemberId(rice.p2p.commonapi.Id id) {
    return isMember((Id) id);
  }
  
  /**
   * Returns a new, empty IdSet of this type
   *
   * @return A new IdSet
   */
  public rice.p2p.commonapi.IdSet build() {
    return new IdSet();
  }
  
  /**
   * return a subset of this set, consisting of the member ids in a given range
   * @param from the lower end of the range (inclusive)
   * @param to the upper end of the range (exclusive)
   * @return the subset
   */
  public rice.p2p.commonapi.IdSet subSet(rice.p2p.commonapi.IdRange range) {
    //return subSet((Id) range.getCWId(), (Id) range.getCCWId());
    return subSet((IdRange) range);
  }
  
  /**
   * return a hash of this set
   *
   * @return the hash of this set
   */
  public byte[] hash() {
    return getHash();
  }
  
  /**
   * return this set as an array
   * @return the array
   */
  public rice.p2p.commonapi.Id[] asArray() {
    return (rice.p2p.commonapi.Id[]) idSet.keySet().toArray(new rice.p2p.commonapi.Id[0]);
  }
  
  public Object clone(){
    return new IdSet(idSet);
  }


}
