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

package rice.pastry.multiring;

import java.io.*;

import rice.pastry.*;

/**
 * This class represents a ringId, which is based on a NodeId and represents
 * a unique identifier for a ring.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */
public class RingId implements Comparable, Serializable {
  
  /**
   * This is the bit length of the node ids.  If it is n, then
   * there are 2^n different Pastry nodes.  We currently assume
   * that it is divisible by 32.
   */
  public final static int ringIdBitLength = 128;

  // elements in the array.
  private final static int nlen = ringIdBitLength / 32;
  private int ringId[];

  /**
   * Constructor.
   *
   * @param material an array of length at least ringIdBitLength/8 containing raw ringId material.
   */
  public RingId(byte material[]) {
    ringId = new int[nlen];

    for (int j=0; j<ringIdBitLength/8; j++) {
      int k = material[j] & 0xff;
      ringId[j / 4] |= k << ((j % 4) * 8);
    }
  }

  /**
   * Constructor.
   *
   * @param material an array of length at least ringIdBitLength/32 containing raw ringId material.
   */
  public RingId(int material[]) {
    ringId = new int[nlen];
    for (int i=0; i<nlen; i++) ringId[i] = material[i];
  }

  /**
   * Constructor.
   *
   * It constructs a new RingId with a value of 0 for all bits.
   */
  public RingId() {
    ringId = new int[nlen];
  }

  /**
   * Blits the ringId into a target array.
   *
   * @param target an array of length at least ringIdBitLength/8 for the ringId to be stored in.
   */
  public void blit(byte target[]) {
    for (int j=0; j<ringIdBitLength/8; j++) {
      int k = ringId[j / 4] >> ((j % 4) * 8);
      target[j] = (byte)(k & 0xff);
    }
  }

  /**
   * Copy the ringId into a new Nodeid
   *
   * @return a new nodeId with the value of this ringId
   */
  public NodeId toNodeId() {
    return new NodeId(copy());
  }

  /**
   * Copy the ringId into a freshly generated array.
   *
   * @return a fresh copy of the ringId material
   */
  public byte [] copy() {
    byte target[] = new byte[ringIdBitLength/8];
    blit(target);
    return target;
  }

  /**
   * return the number of digits in a given base
   *
   * @param base the number of bits in the base
   * @return the number of digits in that base
   */
  public static int numDigits(int base) {
    return ringIdBitLength / base;
  }


  /**
   * Equality operator for ringIds.
   *
   * @param obj a ringId object
   * @return true if they are equal, false otherwise.
   */
  public boolean equals(Object obj) {
    RingId nid = (RingId) obj;

    for (int i=0; i<nlen; i++)
      if (ringId[i] != nid.ringId[i]) return false;

    return true;
  }

  /**
   * Comparison operator for ringIds.
   *
   * The comparison that occurs is a numerical comparison.
   *
   * @param obj the RingId to compare with.
   * @return negative if this < obj, 0 if they are equal and positive if this > obj.
   */
  public int compareTo(Object obj) {
    RingId oth = (RingId) obj;

    for (int i=nlen-1; i >= 0; i--) {
      if (ringId[i] != oth.ringId[i]) {
        long t = ringId[i] & 0x0ffffffffL;
        long o = oth.ringId[i] & 0x0ffffffffL;
        if (t < o) return -1;
        else return 1;
      }
    }

    return 0;
  }

  /**
   * Hash codes for ringIds.
   *
   * @return a hash code.
   */

  public int hashCode() {
    int h = 0;

    /// Hash function is computed by XORing the bits of the ringId.
    for (int i=0; i<nlen; i++)
      h ^= ringId[i];

    return h;
  }

  /**
   * Equivalence relation for ringIds.
   *
   * @param nid the other node id.
   * @return true if they are equal, false otherwise.
   */

  public boolean equals(RingId nid) {
    if(nid == null) return false;

    for (int i=0; i<nlen; i++)
      if (ringId[i] != nid.ringId[i]) return false;

    return true;
  }

  /**
   * Gets the ith digit in base 2^b.
   *
   * i = 0 is the least significant digit.
   *
   * @param i which digit to get.
   * @param b which power of 2 is the base to get it in.
   *
   * @return the ith digit in base 2^b.
   */
  public int getDigit(int i, int b) {
    int bitIndex = b * i + (ringIdBitLength % b);
    int index = bitIndex / 32;
    int shift = bitIndex % 32;

    long val = ringId[index];
    if (shift + b > 32) val = (val & 0xffffffffL) | (((long) ringId[index+1]) << 32);
    return ((int)(val >> shift)) & ((1 << b) - 1);
  }

  /**
   * Returns a string representation of the ringId in base 16.
   *
   * The string is a byte string from most to least significant.
   */
  public String toString() {
    String s = "0x";

    String tran[] = { "0", "1", "2", "3", "4", "5", "6", "7",
      "8", "9", "A", "B", "C", "D", "E", "F" };

    int n = ringIdBitLength / 4;

    for (int i=n-1; i>=0; i--) {
      int d = getDigit(i, 4);

      s = s + tran[d];
    }

    return "<" + s.substring(0,6) + "..>";
  }
}

