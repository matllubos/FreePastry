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
package rice.p2p.past.gc;

import java.math.*;
import java.security.*;
import java.util.*;

import rice.p2p.commonapi.*;

/**
 * @(#) GCIdSet.java
 *
 * Internal representation of a set of GCIds
 * 
 * @version $Id$
 *
 * @author Alan Mislove
 * @author Peter Druschel
 */
public class GCIdSet implements IdSet {
  
  // internal representation of the ids
  protected Vector ids;
  
  // the way to make ids
  protected IdFactory factory;
  
  /**
   * Constructor
   */
  protected GCIdSet(IdFactory factory) {
    this.ids = new Vector();
    this.factory = factory;
  }
  
  /**
   * return the number of elements
   */
  public int numElements() {
    return ids.size();
  }
  
  /**
   * add a member
   * @param id the id to add
   */
  public void addId(Id id) {
    if (! ids.contains(id))
      ids.add(id);
  }
  
  /**
    * remove a member
   * @param id the id to remove
   */
  public void removeId(Id id) {
    ids.remove(id);
  }
  
  /**
    * test membership
   * @param id the id to test
   * @return true of id is a member, false otherwise
   */
  public boolean isMemberId(Id id) {
    return ids.contains(id);
  }
  
  /**
   * return a subset of this set, consisting of the member ids in a given range
   * @param from the lower end of the range (inclusive)
   * @param to the upper end of the range (exclusive)
   * @return the subset
   */
  public IdSet subSet(IdRange range) {
    GCIdSet result = new GCIdSet(factory);
    
    for (int i=0; i<ids.size(); i++) 
      if (range.containsId(((GCId) ids.elementAt(i)))) 
        result.addId((GCId) ids.elementAt(i));
    
    return result;
  }
  
  /**
   * return an iterator over the elements of this set
   * @return the interator
   */
  public Iterator getIterator() {
    return ids.iterator();
  }
  
  /**
   * return this set as an array
   * @return the array
   */
  public Id[] asArray() {
    return (Id[]) ids.toArray(new Id[0]);
  }
  
  /**
   * return a hash of this set
   *
   * @return the hash of this set
   */
  public Id hash() {
    MessageDigest md = null;
    try {
      md = MessageDigest.getInstance("SHA");
    } catch (NoSuchAlgorithmException e) {
      System.err.println("No SHA support!");
      return null;
    }
    
    Id[] array = asArray();
    Arrays.sort(array);
         
    for (int i=0; i<array.length; i++) {
      GCId id = (GCId) array[i];
      md.update(id.getId().toByteArray());
      md.update(new BigInteger("" + id.getExpiration()).toByteArray());
    }
    
    return new GCId(factory.buildId(md.digest()), GCPast.INFINITY_EXPIRATION);
  }
  
  /**
   * Determines equality
   *
   * @param other To compare to
   * @return Equals
   */
  public boolean equals(Object o) {
    GCIdSet other = (GCIdSet) o;
    
    if (numElements() != other.numElements())
      return false;
    
    for (int i=0; i<ids.size(); i++)
      if (! other.isMemberId((Id) ids.elementAt(i)))
        return false;
    
    return true;
  }
  
  /**
   * Returns the hashCode
   *
   * @return hashCode
   */
  public int hashCode() {
    return ids.hashCode();
  }
  
  /**
   * Prints out the string
   *
   * @return A string
   */
  public String toString() {
    return "{GCIdSet of size " + numElements() + "}";
  }
  
  /**
   * Clones this object
   *
   * @return a clone
   */
  public Object clone() {
    return ids.clone();
  }
}
