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

package rice.pastry.commonapi;

import rice.p2p.commonapi.*;

import java.lang.Comparable;
import java.lang.ref.*;
import java.io.*;
import java.util.*;
import java.security.*;

/**
 * This class provides applications with a way of genertating
 * pastry Ids.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 * @author Peter Druschel
 */
public class PastryIdFactory implements IdFactory {
  
  /**
   * Static hashtable of ringIds, for coalescing multiple ids
   */
  protected static WeakHashMap ID_CACHE = new WeakHashMap();

  private MessageDigest md;

  /**
   * Constructor
   */
  public PastryIdFactory() {
    try {
      md = MessageDigest.getInstance("SHA");
    } catch ( NoSuchAlgorithmException e ) {
      System.err.println( "No SHA support!" );
    }
  }
  
  /**
   * Method which ensures that only one copy of each thingy is ever stored
   *
   * @param input The Id to be returned
   * @return THe canocotial object
   */
  protected Id process(Id input) {
    WeakReference ref = (WeakReference) ID_CACHE.get(input);
    Id result = null;
    
    if ((ref != null) && ((result = (Id) ref.get()) != null)) {
      return result;
    } else {
      ID_CACHE.put(input, new WeakReference(input));
      return input;
    }
  }
      
  /**
   * Builds a protocol-specific Id given the source data.
   *
   * @param material The material to use
   * @return The built Id.
   */
  public Id buildId(byte[] material) {
    return process(new rice.pastry.Id(material));
  }

  /**
   * Builds a protocol-specific Id given the source data.
   *
   * @param material The material to use
   * @return The built Id.
   */
  public Id buildId(int[] material) {
    return process(new rice.pastry.Id(material));
  }

  /**
   * Builds a protocol-specific Id by using the hash of the given string as source data.
   *
   * @param string The string to use as source data
   * @return The built Id.
   */
  public Id buildId(String string) {
    md.update(string.getBytes());
    return buildId(md.digest());
  }
  
  /**
   * Builds an Id by converting the given toString() output back to an Id.  Should
   * not normally be used.
   *
   * @param string The toString() representation of an Id
   * @return The built Id.
   */
  public Id buildIdFromToString(String string) {
    return process(new rice.pastry.Id(string));
  }
  
  /**
   * Builds an Id by converting the given toString() output back to an Id.  Should
   * not normally be used.
   *
   * @param chars The character array
   * @param offset The offset to start reading at
   * @param length The length to read
   * @return The built Id.
   */
  public Id buildIdFromToString(char[] chars, int offset, int length) {
    return process(new rice.pastry.Id(chars, offset, length));
  }
  
  /**
   * Builds a protocol-specific Id.Distance given the source data.
   *
   * @param material The material to use
   * @return The built Id.Distance.
   */
  public Id.Distance buildIdDistance(byte[] material) {
    return new rice.pastry.Id.Distance(material);
  }

  /**
   * Creates an IdRange given the CW and CCW ids.
   *
   * @param cw The clockwise Id
   * @param ccw The counterclockwise Id
   * @return An IdRange with the appropriate delimiters.
   */
  public IdRange buildIdRange(Id cw, Id ccw) {
    return new rice.pastry.IdRange((rice.pastry.Id) cw, (rice.pastry.Id) ccw);
  }

  /**
   * Creates an empty IdSet.
   *
   * @return an empty IdSet
   */
  public IdSet buildIdSet() {
    return new rice.pastry.IdSet();
  }
  
  /**
   * Creates an empty NodeHandleSet.
   *
   * @return an empty NodeHandleSet
   */
  public NodeHandleSet buildNodeHandleSet() {
    return new rice.pastry.NodeSet();
  }
}

