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

import java.util.*;

import rice.p2p.commonapi.*;

/**
 * @(#) GCIdFactory.java 
 *
 * This class provides the ability to build Ids which can support a multi-ring
 * hierarchy.
 *
 * @version $Id$
 * @author Alan Mislove
 * @author Peter Druschel
 */
public class GCIdFactory implements IdFactory {
  
  /**
   * The underlying IdFactory
   */
  protected IdFactory factory;
  
  /**
   * Constructor
   *
   * @param factory the underlying factory to use
   */
  public GCIdFactory(IdFactory factory) {
    this.factory = factory;
  }
  
  /**
   * Builds a protocol-specific Id given the source data.
   *
   * @param material The material to use
   * @return The built Id.
   */
  public Id buildNormalId(byte[] material) {
    throw new UnsupportedOperationException("GCIdFactory cannot be used to build Ids!");
  }
  
  /**
   * Builds a protocol-specific Id given the source data.
   *
   * @param material The material to use
   * @return The built Id.
   */
  public Id buildId(byte[] material) {
    throw new UnsupportedOperationException("GCIdFactory cannot be used to build Ids!");
  }
  
  /**
   * Builds a protocol-specific Id given the source data.
   *
   * @param material The material to use
   * @return The built Id.
   */
  public Id buildId(int[] material) {
    throw new UnsupportedOperationException("GCIdFactory cannot be used to build Ids!");
  }
  
  /**
   * Builds a protocol-specific Id by using the hash of the given string as source data.
   *
   * @param string The string to use as source data
   * @return The built Id.
   */
  public Id buildId(String string) {
    throw new UnsupportedOperationException("GCIdFactory cannot be used to build Ids!");
  }
  
  /**
   * Builds a random protocol-specific Id.
   *
   * @param rng A random number generator
   * @return The built Id.
   */
  public Id buildRandomId(Random rng) {
    throw new UnsupportedOperationException("GCIdFactory cannot be used to build Ids!");
  }

  /**
   * Builds an Id by converting the given toString() output back to an Id.  Should
   * not normall be used.
   *
   * @param string The toString() representation of an Id
   * @return The built Id.
   */
  public Id buildIdFromToString(String string) {
    throw new UnsupportedOperationException("GCIdFactory cannot be used to build Ids!");
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
    throw new UnsupportedOperationException("GCIdFactory cannot be used to build Ids!");  
  }
  
  /**
   * Returns the length a Id.toString should be.
   *
   * @return The correct length;
   */
  public int getIdToStringLength() {
    throw new UnsupportedOperationException("GCIdFactory cannot be used to build Ids!");
  }
  
  /**
   * Builds a protocol-specific Id.Distance given the source data.
   *
   * @param material The material to use
   * @return The built Id.Distance.
   */
  public Id.Distance buildIdDistance(byte[] material) {
    throw new UnsupportedOperationException("GCIdFactory cannot be used to build IdDistances!");  }
  
  /**
   * Creates an IdRange given the CW and CCW ids.
   *
   * @param cw The clockwise Id
   * @param ccw The counterclockwise Id
   * @return An IdRange with the appropriate delimiters.
   */
  public IdRange buildIdRange(Id cw, Id ccw) {
    return factory.buildIdRange(cw, ccw);
  }
  
  /**
   * Creates an empty IdSet.
   *
   * @return an empty IdSet
   */
  public IdSet buildIdSet() {
    return new GCIdSet();
  }
  
  /**
   * Creates an empty NodeHandleSet.
   *
   * @return an empty NodeHandleSet
   */
  public NodeHandleSet buildNodeHandleSet() {
    throw new UnsupportedOperationException("GCIdFactory cannot be used to build NodeHandleSets!");
  }
}

