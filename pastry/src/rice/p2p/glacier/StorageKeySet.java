package rice.p2p.glacier;

import rice.p2p.glacier.*;
import rice.p2p.commonapi.*;
import java.io.Serializable;
import java.util.*;
import java.security.*;

public class StorageKeySet implements rice.p2p.commonapi.IdSet {

  private TreeSet idSet;

  // a cache of the fingerprint hash
  private StorageKey cachedHash;
  private boolean validHash;

  /**
   * Constructor.
   */
  public StorageKeySet() {
    idSet = new TreeSet();
    validHash = false;
  }

  /**
   * Constructor.
   * constructs a shallow copy of the given TreeSet s.
   * @param s the TreeSet based on which we construct a new IdSet
   */
  protected StorageKeySet(TreeSet s) {
    idSet = new TreeSet(s);
    validHash = false;
  }

  /**
   * Copy constructor.
   * constructs a shallow copy of the given IdSet o.
   * @param o the IdSet to copy
   */
  public StorageKeySet(StorageKeySet o) {
    idSet = new TreeSet(o.idSet);
    cachedHash = o.cachedHash;
    validHash = o.validHash;
  }

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
  public void addMember(rice.p2p.commonapi.Id id) {
    idSet.add(id);
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
  public boolean isMember(rice.p2p.commonapi.Id id) {
    return idSet.contains(id);
  }

  /**
   * return the smallest member id
   * @return the smallest id in the set
   */
  public StorageKey minMember() {
    return (StorageKey)idSet.first();
  }

  /**
   * return the largest member id
   * @return the largest id in the set
   */
  public StorageKey maxMember() {
    return (StorageKey)idSet.last();
  }

  /**
   * return a subset of this set, consisting of the member ids in a given range
   * @param from the counterclockwise end of the range (inclusive)
   * @param to the clockwise end of the range (exclusive)
   * @return the subset
   */
  public StorageKeySet subSet(Id from, Id to) {
    System.err.println("StorageKeySet.subSet(2) called");
    System.exit(1);
    return null;

/*    StorageKeySet res;

    if (from.compareTo(to) <= 0) {
      res = new StorageKeySet( (TreeSet) idSet.subSet(from, to));
    } else {
      res = new StorageKeySet( (TreeSet) idSet.tailSet(from));
      res.idSet.addAll(idSet.headSet(to));
    }

    return res; */
  }

  /**
   * return a subset of this set, consisting of the member ids in a given range
   * @param range the range
   * @return the subset
   */
  public StorageKeySet subSet(StorageKeyRange range) {

    System.err.println("StorageKeySet.subSet() called");
    System.exit(1);
    return null;

/*    if (range.isEmpty()) {
      return new StorageKeySet();
    } else if (range.getCCW().equals(range.getCW())) {
      return this;
    } else {
      return subSet(range.getCCW(), range.getCW());
    } */
  }

  /**
   * return an iterator over the elements of this set
   * @return the interator
   */
  public Iterator getIterator() {
    return idSet.iterator();
  }

  /**
   * compute a fingerprint of the members in this IdSet
   *
   * @return an Id containing the secure hash of this set
   */

  public Id getHash() {

    System.err.println("StorageKeySet.getHash() called");
    System.exit(1);
    return null;

/*    if (validHash) return cachedHash;

    // recompute the hash
    MessageDigest md = null;
    try {
      md = MessageDigest.getInstance("SHA");
    } catch ( NoSuchAlgorithmException e ) {
      System.err.println( "No SHA support!" );
      return null;
    }

    Iterator it = idSet.iterator();
    byte[] raw = new byte[Id.IdBitLength / 8];
    Id id;

    while (it.hasNext()) {
      id = (Id) it.next();
      id.blit(raw);
      md.update(raw);
    }

    byte[] digest = md.digest();
    cachedHash = new Id(digest);
    validHash = true;

    return cachedHash; */
  }



  /**
   * Returns a string representation of the IdSet.
   */

  public String toString()
  {
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
    addMember(id);
  }

  /**
   * remove a member
   * @param id the id to remove
   */
  public void removeId(rice.p2p.commonapi.Id id) {
    removeMember((StorageKey) id);
  }

  /**
   * test membership
   * @param id the id to test
   * @return true of id is a member, false otherwise
   */
  public boolean isMemberId(rice.p2p.commonapi.Id id) {
    return isMember(id);
  }
  
  /**
   * return a subset of this set, consisting of the member ids in a given range
   * @param from the lower end of the range (inclusive)
   * @param to the upper end of the range (exclusive)
   * @return the subset
   */
  public rice.p2p.commonapi.IdSet subSet(rice.p2p.commonapi.IdRange range) {
    //return subSet((Id) range.getCWId(), (Id) range.getCCWId());
    return subSet((StorageKeyRange)range);
  }
  
  /**
   * return a hash of this set
   *
   * @return the hash of this set
   */
  public rice.p2p.commonapi.Id hash() {
    return getHash();
  }


}
