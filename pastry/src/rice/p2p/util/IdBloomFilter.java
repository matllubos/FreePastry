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

- Neither the name of Rice University (RICE) nor the names of its
contributors may be used to endorse or promote products derived from
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
  
  /**
   * The number of bits per key in bloom filters
   */
  public static int NUM_BITS_PER_KEY = 4;

  /**
   * The number of different hash functions to use in bloom filters
   */
  public static int NUM_HASH_FUNCTIONS = 2;
  
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
   * Method which adds an Id to the underlying bloom filter
   *
   * @param id The id to add
   */
  protected void addId(Id id) {
    filter.add(id.toByteArray());
  }
  
  /**
   * Method which returns whether or not an Id *may* be in the set.  Specifically, 
   * if this method returns false, the element is definately not in the set.  Otherwise, 
   * if true is returned, the element may be in the set, but it is not guaranteed.
   *
   * @param id The id to check for
   */
  public boolean check(Id id) {
    return filter.check(id.toByteArray());
  }
}