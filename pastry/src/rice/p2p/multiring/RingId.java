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

package rice.p2p.multiring;

import rice.p2p.commonapi.*;

/**
 * @(#) RingId.java
 *
 * This class represents an Id with a specific ring attached.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */
public class RingId implements Id {
  
  /**
   * The id which this ringId represents
   */
  protected Id id;
  
  /**
   * The ringId which this ringId represents
   */
  protected Id ringId;
  
  /**
   * Constructor
   *
   * @param node The node to base this node off of
   */
  protected RingId(Id ringId, Id id) {
    this.id = id;
    this.ringId = ringId;
    
    if (id instanceof RingId)
      throw new IllegalArgumentException("RingId created with id as a RingId!" + ringId + " " + id);
    if (ringId instanceof RingId)
      throw new IllegalArgumentException("RingId created with ringId as a RingId!" + ringId + " " + id);
  }
  
  /**
   * Returns this ringid's id
   *
   * @return The id of this ringid
   */
  public Id getId() {
    return id;
  }
  
  /**
   * Returns this ringid's ring id
   *
   * @return The ring id of this ringid
   */
  public Id getRingId() {
    return ringId;
  }
  
  /**
   * Checks if this Id is between two given ids ccw (inclusive) and cw (exclusive) on the circle
   *
   * @param ccw the counterclockwise id
   * @param cw the clockwise id
   * @return true if this is between ccw (inclusive) and cw (exclusive), false otherwise
   */
  public boolean isBetween(Id ccw, Id cw) {
    return id.isBetween(ccw, cw);
  }
  
  /**
   * Checks to see if the Id nid is clockwise or counterclockwise from this, on the ring. An Id is
   * clockwise if it is within the half circle clockwise from this on the ring. An Id is considered
   * counter-clockwise from itself.
   *
   * @param nid The id to compare to
   * @return true if clockwise, false otherwise.
   */
  public boolean clockwise(Id nid) {
    return id.clockwise(nid);
  }
  
  /**
   * Returns an Id corresponding to this Id plus a given distance
   *
   * @param offset the distance to add
   * @return the new Id
   */
  public Id addToId(Distance offset) {
    return new RingId(ringId, id.addToId(offset));
  }
  
  /**
   * Returns the shorter numerical distance on the ring between a pair of Ids.
   *
   * @param nid the other node id.
   * @return the distance between this and nid.
   */
  public Distance distanceFromId(Id nid) {
    return id.distanceFromId(nid);
  }
  
  /**
   * Returns the longer numerical distance on the ring between a pair of Ids.
   *
   * @param nid the other node id.
   * @return the distance between this and nid.
   */
  public Distance longDistanceFromId(Id nid) {
    return id.longDistanceFromId(nid);
  }
  
  /**
   * Returns a (mutable) byte array representing this Id
   *
   * @return A byte[] representing this Id
   */
  public byte[] toByteArray() {
    return id.toByteArray();
  }
  
  /**
   * Returns whether or not this object is equal to the provided one
   *
   * @param o The object to compare to
   * @return Whether or not this is object is equal
   */
  public boolean equals(Object o) {
    if (! (o instanceof RingId))
      return false;
    
    return (((RingId) o).id.equals(id) && ((RingId) o).ringId.equals(ringId));
  }
  
  /**
   * Returns the hashCode
   *
   * @return hashCode
   */
  public int hashCode() {
    return (id.hashCode() + ringId.hashCode());
  }
  
  /**
   * Returns a string representing this ring Id.
   *
   * @return A string with all of this Id
   */
  public String toString() {
    return "(" + ringId + ", " + id + ")";
  }
  
  /**
   * Returns a string representing the full length of this Id.
   *
   * @return A string with all of this Id
   */
  public String toStringFull() {
    return "(" + ringId.toStringFull() + ", " + id.toStringFull() + ")";
  }
  
  /**
   * Returns this id compared to the target
   *
   * @return The comparison
   */
  public int compareTo(Object o) {
    return id.compareTo(o);
  }
}




