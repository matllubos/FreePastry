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

package rice.p2p.multiring;

import rice.p2p.commonapi.*;

/**
 * @(#) IdFactory.java 
 *
 * This class provides the ability to build Ids which can support a multi-ring
 * hierarchy.
 *
 * @version $Id$
 * @author Alan Mislove
 * @author Peter Druschel
 */
public class MultiringIdFactory implements IdFactory {
  
  /**
   * The multiring node supporting this endpoint
   */
  protected MultiringNode node;
  
  /**
   * The underlying IdFactory
   */
  protected IdFactory factory;
  
  /**
   * Constructor
   *
   * @param factory the underlying factory to use
   */
  protected MultiringIdFactory(MultiringNode node, IdFactory factory) {
    this.node = node;
    this.factory = factory;
  }
  
  /**
   * Method which returns the underlying Id which represents the local
   * node's ring
   *
   * @return The Id represetning the local ring
   */
  protected Id getRingId() {
    return node.getRingId();
  }
  
  /**
   * Builds a ringId by using the provided Id and ringIds.
   *
   * @param ringId The id to use as the ringid
   * @param id The id to use as the id
   * @return The built Id.
   */
  public RingId buildRingId(Id ringId, Id id) {
    return new RingId(ringId, id);
  }
  
  /**
   * Builds a protocol-specific Id given the source data.
   *
   * @param material The material to use
   * @return The built Id.
   */
  protected Id buildNormalId(byte[] material) {
    return factory.buildId(material);
  }
  
  /**
   * Builds a protocol-specific Id given the source data.
   *
   * @param material The material to use
   * @return The built Id.
   */
  public Id buildId(byte[] material) {
    return new RingId(getRingId(), factory.buildId(material));
  }
  
  /**
   * Builds a protocol-specific Id given the source data.
   *
   * @param material The material to use
   * @return The built Id.
   */
  public Id buildId(int[] material) {
    return new RingId(getRingId(), factory.buildId(material));
  }
  
  /**
   * Builds a protocol-specific Id by using the hash of the given string as source data.
   *
   * @param string The string to use as source data
   * @return The built Id.
   */
  public Id buildId(String string) {
    return new RingId(getRingId(), factory.buildId(string));
  }
  
  /**
   * Builds a protocol-specific Id.Distance given the source data.
   *
   * @param material The material to use
   * @return The built Id.Distance.
   */
  public Id.Distance buildIdDistance(byte[] material) {
    return factory.buildIdDistance(material);
  }
  
  /**
   * Creates an IdRange given the CW and CCW ids.
   *
   * @param cw The clockwise Id
   * @param ccw The counterclockwise Id
   * @return An IdRange with the appropriate delimiters.
   */
  public IdRange buildIdRange(Id cw, Id ccw) {
    return new MultiringIdRange(getRingId(), factory.buildIdRange(((RingId) cw).getId(), ((RingId) ccw).getId()));
  }
  
  /**
   * Creates an empty IdSet.
   *
   * @return an empty IdSet
   */
  public IdSet buildIdSet() {
    return new MultiringIdSet(getRingId(), factory.buildIdSet());
  }
  
  /**
   * Creates an empty NodeHandleSet.
   *
   * @return an empty NodeHandleSet
   */
  public NodeHandleSet buildNodeHandleSet() {
    return new MultiringNodeHandleSet(getRingId(), factory.buildNodeHandleSet());
  }
}

