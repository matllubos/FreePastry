package rice.p2p.glacier;
import java.io.Serializable;
import java.security.*;
import java.util.*;
import rice.p2p.commonapi.*;

import rice.p2p.glacier.*;

/**
 * DESCRIBE THE CLASS
 *
 * @version $Id$
 * @author ahae
 */
public class VersionKeySet implements rice.p2p.commonapi.IdSet {

  private TreeSet idSet;

  // a cache of the fingerprint hash
  private VersionKey cachedHash;
  private boolean validHash;

  /**
   * Constructor.
   */
  public VersionKeySet() {
    idSet = new TreeSet();
    validHash = false;
  }

  /**
   * Copy constructor. constructs a shallow copy of the given IdSet o.
   *
   * @param o the IdSet to copy
   */
  public VersionKeySet(VersionKeySet o) {
    idSet = new TreeSet(o.idSet);
    cachedHash = o.cachedHash;
    validHash = o.validHash;
  }

  /**
   * Constructor. constructs a shallow copy of the given TreeSet s.
   *
   * @param s the TreeSet based on which we construct a new IdSet
   */
  protected VersionKeySet(TreeSet s) {
    idSet = new TreeSet(s);
    validHash = false;
  }

  /**
   * test membership
   *
   * @param id the id to test
   * @return true of id is a member, false otherwise
   */
  public boolean isMember(rice.p2p.commonapi.Id id) {
    return idSet.contains(id);
  }

  /**
   * return an iterator over the elements of this set
   *
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

    System.err.println("VersionKeySet.getHash() called");
    System.exit(1);
    return null;
    /*
     *  if (validHash) return cachedHash;
     *  / recompute the hash
     *  MessageDigest md = null;
     *  try {
     *  md = MessageDigest.getInstance("SHA");
     *  } catch ( NoSuchAlgorithmException e ) {
     *  System.err.println( "No SHA support!" );
     *  return null;
     *  }
     *  Iterator it = idSet.iterator();
     *  byte[] raw = new byte[Id.IdBitLength / 8];
     *  Id id;
     *  while (it.hasNext()) {
     *  id = (Id) it.next();
     *  id.blit(raw);
     *  md.update(raw);
     *  }
     *  byte[] digest = md.digest();
     *  cachedHash = new Id(digest);
     *  validHash = true;
     *  return cachedHash;
     */
  }

  /**
   * test membership
   *
   * @param id the id to test
   * @return true of id is a member, false otherwise
   */
  public boolean isMemberId(rice.p2p.commonapi.Id id) {
    return isMember(id);
  }

  /**
   * return the number of elements
   *
   * @return DESCRIBE THE RETURN VALUE
   */
  public int numElements() {
    return idSet.size();
  }

  /**
   * add a member
   *
   * @param id the id to add
   */
  public void addMember(rice.p2p.commonapi.Id id) {
    idSet.add(id);
    validHash = false;
  }

  /**
   * remove a member
   *
   * @param id the id to remove
   */
  public void removeMember(Id id) {
    idSet.remove(id);
    validHash = false;
  }

  /**
   * return the smallest member id
   *
   * @return the smallest id in the set
   */
  public VersionKey minMember() {
    return (VersionKey) idSet.first();
  }

  /**
   * return the largest member id
   *
   * @return the largest id in the set
   */
  public VersionKey maxMember() {
    return (VersionKey) idSet.last();
  }

  /**
   * return a subset of this set, consisting of the member ids in a given range
   *
   * @param from the counterclockwise end of the range (inclusive)
   * @param to the clockwise end of the range (exclusive)
   * @return the subset
   */
  public VersionKeySet subSet(Id from, Id to) {
    System.err.println("VersionKeySet.subSet(2) called");
    System.exit(1);
    return null;
    /*
     *  VersionKeySet res;
     *  if (from.compareTo(to) <= 0) {
     *  res = new VersionKeySet( (TreeSet) idSet.subSet(from, to));
     *  } else {
     *  res = new VersionKeySet( (TreeSet) idSet.tailSet(from));
     *  res.idSet.addAll(idSet.headSet(to));
     *  }
     *  return res;
     */
  }

  /**
   * return a subset of this set, consisting of the member ids in a given range
   *
   * @param range the range
   * @return the subset
   */
  public VersionKeySet subSet(VersionKeyRange range) {

    System.err.println("VersionKeySet.subSet() called");
    System.exit(1);
    return null;
    /*
     *  if (range.isEmpty()) {
     *  return new VersionKeySet();
     *  } else if (range.getCCW().equals(range.getCW())) {
     *  return this;
     *  } else {
     *  return subSet(range.getCCW(), range.getCW());
     *  }
     */
  }


  /**
   * Returns a string representation of the IdSet.
   *
   * @return DESCRIBE THE RETURN VALUE
   */

  public String toString() {
    Iterator it = getIterator();
    Id key;
    String s = "[ IdSet: ";
    while (it.hasNext()) {
      key = (Id) it.next();
      s = s + key + ",";

    }
    s = s + " ]";
    return s;
  }

  // Common API Support

  /**
   * add a member
   *
   * @param id the id to add
   */
  public void addId(rice.p2p.commonapi.Id id) {
    addMember(id);
  }

  /**
   * remove a member
   *
   * @param id the id to remove
   */
  public void removeId(rice.p2p.commonapi.Id id) {
    removeMember((VersionKey) id);
  }

  /**
   * return a subset of this set, consisting of the member ids in a given range
   *
   * @param range DESCRIBE THE PARAMETER
   * @return the subset
   */
  public rice.p2p.commonapi.IdSet subSet(rice.p2p.commonapi.IdRange range) {
    //return subSet((Id) range.getCWId(), (Id) range.getCCWId());
    return subSet((VersionKeyRange) range);
  }

  /**
   * return a hash of this set
   *
   * @return the hash of this set
   */
  public rice.p2p.commonapi.Id hash() {
    return getHash();
  }

  /**
   * DESCRIBE THE METHOD
   *
   * @return DESCRIBE THE RETURN VALUE
   */
  public Object clone() {
    return new VersionKeySet(this);
  }

  /**
   * return this set as an array
   * @return the array
   */
  public rice.p2p.commonapi.Id[] asArray() {
    return (rice.p2p.commonapi.Id[]) idSet.toArray(new rice.p2p.commonapi.Id[0]);
  }
}
