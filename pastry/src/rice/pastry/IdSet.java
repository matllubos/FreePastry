/*************************************************************************

"Free Pastry" Peer-to-Peer Application Development Substrate

Copyright 2002, Rice University. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither  the name  of Rice  University (RICE) nor  the names  of its
contributors may be  used to endorse or promote  products derived from
this software without specific prior written permission.

This software is provided by RICE and the contributors on an "as is"
basis, without any representations or warranties of any kind, express
or implied including, but not limited to, representations or
warranties of non-infringement, merchantability or fitness for a
particular purpose. In no event shall RICE or contributors be liable
for any direct, indirect, incidental, special, exemplary, or
consequential damages (including, but not limited to, procurement of
substitute goods or services; loss of use, data, or profits; or
business interruption) however caused and on any theory of liability,
whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even
if advised of the possibility of such damage.

********************************************************************************/

package rice.pastry;

import java.io.Serializable;
import java.util.*;
import java.security.*;

/**
 * Represents a set of Pastry ids.
 * *
 * @version $Id$
 *
 * @author Peter Druschel
 */

public class IdSet implements rice.p2p.commonapi.IdSet {

  private TreeSet idSet;

  // a cache of the fingerprint hash
  private Id cachedHash;
  private boolean validHash;

  /**
   * Constructor.
   */
  public IdSet() {
    idSet = new TreeSet();
    validHash = false;
  }

  /**
   * Constructor.
   * constructs a shallow copy of the given TreeSet s.
   * @param s the TreeSet based on which we construct a new IdSet
   */
  protected IdSet(TreeSet s) {
    idSet = new TreeSet(s);
    validHash = false;
  }

  /**
   * Copy constructor.
   * constructs a shallow copy of the given IdSet o.
   * @param o the IdSet to copy
   */
  public IdSet(IdSet o) {
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
  public void addMember(Id id) {
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
  public boolean isMember(Id id) {
    return idSet.contains(id);
  }

  /**
   * return the smallest member id
   * @return the smallest id in the set
   */
  public Id minMember() {
    return (Id) idSet.first();
  }

  /**
   * return the largest member id
   * @return the largest id in the set
   */
  public Id maxMember() {
    return (Id) idSet.last();
  }

  /**
   * return a subset of this set, consisting of the member ids in a given range
   * @param from the counterclockwise end of the range (inclusive)
   * @param to the clockwise end of the range (exclusive)
   * @return the subset
   */
  public IdSet subSet(Id from, Id to) {
    IdSet res;
    if (from.compareTo(to) <= 0) {
      res = new IdSet( (TreeSet) idSet.subSet(from, to));
    } else {
      SortedSet ss = idSet.tailSet(from);
      ss.addAll(idSet.headSet(to));
      res = new IdSet( (TreeSet) ss);
    }

    return res;
  }

  /**
   * return a subset of this set, consisting of the member ids in a given range
   * @param range the range
   * @return the subset
   */
  public IdSet subSet(IdRange range) {
    return subSet(range.getCCW(), range.getCW());
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

    if (validHash) return cachedHash;

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

    return cachedHash;
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
   * return a subset of this set, consisting of the member ids in a given range
   * @param from the lower end of the range (inclusive)
   * @param to the upper end of the range (exclusive)
   * @return the subset
   */
  public rice.p2p.commonapi.IdSet subSet(rice.p2p.commonapi.IdRange range) {
    //return subSet((Id) range.getCWId(), (Id) range.getCCWId());
    return subSet((IdRange)range);
  }


}
