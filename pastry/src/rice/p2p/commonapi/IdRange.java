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

package rice.p2p.commonapi;

import java.util.*;

/**
 * Represents a contiguous range of Ids.
 * 
 * @version $Id$
 *
 * @author Alan Mislove
 * @author Peter Druschel
 */
public class IdRange {
  
  private boolean empty;
  private Id ccw;
  private Id cw;

  /**
   * Constructor.
   *
   * @param ccw the id at the counterclockwise edge of the range (inclusive)
   * @param cw the id at the clockwise edge of the range (exclusive)
   */
  public IdRange(Id ccw, Id cw) {
    empty = false;
    this.ccw = ccw;
    this.cw = cw;
  }

  /**
   * Constructor, constructs an empty IdRange
   *
   */
  public IdRange() {
    empty = true;
  }

  /**
   * Copy constructor.
   */
  public IdRange(IdRange o) {
    this.empty = o.empty;
    this.ccw  = o.ccw;
    this.cw = o.cw;
  }


  /**
   * equality operator
   *
   * @param obj the other IdRange
   * @return true if the IdRanges are equal
   */
  public boolean equals(Object obj) {
    IdRange o = (IdRange) obj;

    if (!empty && !o.empty) {
      return (ccw.equals(o.ccw) && cw.equals(o.cw));
    } else if (empty && o.empty) {
      return true;
    }	else {
      return false;
    }
  }

  /**
   * return the size of the range
   * @return the numerical distance of the range
   */
  public Id.Distance size() {
    if (ccw.clockwise(cw))
      return ccw.distance(cw);
    else
      return ccw.longDistance(cw);
  }

  /**
   * test if the range is empty
   * @return true if the range is empty
   */
  public boolean isEmpty() {
    return empty;
  }

  /**
   * test if a given key lies within this range
   *
   * @param key the key
   * @return true if the key lies within this range, false otherwise
   */
  public boolean contains(Id key) {
    return key.isBetween(ccw, cw);
  }

  /**
   * get counterclockwise edge of range
   * @return the id at the counterclockwise edge of the range (inclusive)
   */
  public Id getCCW() {
    return ccw;
  }

  /**
   * get clockwise edge of range
   * @return the id at the clockwise edge of the range (exclusive)
   */
  public Id getCW() {
    return cw;
  }

  /**
   * set counterclockwise edge of range
   * @param ccw the new id at the counterclockwise edge of the range (inclusive)
   */
  private void setCCW(Id ccw) {
    this.ccw = ccw;
  }

  /**
   * set clockwise edge of range
   * @param cw the new id at the clockwise edge of the range (exclusive)
   */
  private void setCW(Id cw) {
    this.cw = cw;
  }

  /**
   * merge two ranges
   * if this and other don't overlap and are not adjacent, the result is this
   *
   * @param o the other range
   * @return the resulting range
   */
  public IdRange merge(IdRange o) {
    Id newCW, newCCW;

    if (ccw.isBetween(o.ccw, o.cw))
      newCCW = o.ccw;
    else
      newCCW = ccw;

    if (cw.isBetween(o.ccw, o.cw))
      newCW = o.cw;
    else 
      newCW = cw;

    return new IdRange(newCCW, newCW);
  }

  /**
   * intersect two ranges
   * returns an empty range if the ranges don't intersect
   *
   * @param o the other range
   * @return the result range
   */
  public IdRange intersect(IdRange o) {
    Id newCW, newCCW;
    boolean intersect = false;

    if (ccw.isBetween(o.ccw, o.cw)) {
      newCCW = ccw;
      intersect = true;
    } else
      newCCW = o.ccw;

    if (cw.isBetween(o.ccw, o.cw)) {
      newCW = cw;
      intersect = true;
    } else {
      newCW = o.cw;
    }

    if (intersect)
      return new IdRange(newCCW, newCW);
    else
      return new IdRange();

  }

  /**
   * compute the difference between two ranges
   * returns an empty range if the ranges are identical
   *
   * @param o the other range
   * @param cwPart if true, returns the clockwise part of the range difference, else the counterclockwise part
   * @return the result range
   */
  public IdRange diff(IdRange o, boolean cwPart) {

    if (equals(o)) return new IdRange();

    if (!cwPart) {
      if (ccw.isBetween(o.ccw, o.cw))
        return new IdRange(o.ccw, ccw);

      if (o.cw.isBetween(ccw, cw))
        return new IdRange(ccw, o.ccw);

      return this;
    } else {
      if (o.cw.isBetween(ccw, cw))
        return new IdRange(o.cw, cw);

      if (cw.isBetween(o.ccw, o.cw))
        return new IdRange(cw, o.cw);

      return o;
    }
  }

  /**
   * get counterclockwise half of the range
   * @return the range corresponding to the ccw half of this range
   */
  public IdRange ccwHalf() {
    Id newCW = ccw.add(size().shift(1,0));
    return new IdRange(ccw, newCW);
  }

  /**
   * get clockwise half of the range
   * @return the range corresponding to the cw half of this range
   */
  public IdRange cwHalf() {
    Id newCCW = ccw.add(size().shift(1,0));
    return new IdRange(newCCW, cw);
  }

  /**
   * Returns a string representation of the range.
   */
  public String toString() {
    if (empty) return "IdRange: empty";
    else return "IdRange: from:" + ccw + " to:" + cw;
  }

}



