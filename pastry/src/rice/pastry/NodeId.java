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

import java.lang.Comparable;
import java.io.*;
import java.util.*;

/**
 * Represents a Pastry identifier for a node, object or key.
 *
 * A single node identifier and the bit length for nodes is stored in this class.
 * nodeIds are stored little endian.
 *
 * @version $Id$
 *
 * @author Andrew Ladd
 * @author Peter Druschel
 */

public class NodeId implements Comparable, Serializable 
{
    /**
     * This is the bit length of the node ids.  If it is n, then
     * there are 2^n different Pastry nodes.  We currently assume
     * that it is divisible by 32.
     */
    public final static int nodeIdBitLength = 128;

    // elements in the array.
    private final static int nlen = nodeIdBitLength / 32;
    private int nodeId[];

    /**
     * Constructor.
     *
     * @param material an array of length at least nodeIdBitLength/8 containing raw nodeId material.
     */
    public NodeId(byte material[]) 
    {
	nodeId = new int[nlen];
	for (int i=0; i<nlen; i++) nodeId[i] = 0;

	for (int j=0; j<nodeIdBitLength/8; j++) {
	    int k = material[j] & 0xff;
	    nodeId[j / 4] |= k << ((j % 4) * 8);
	}
    }

    /**
     * Constructor.
     *
     * @param material an array of length at least nodeIdBitLength/32 containing raw nodeId material.
     */
    public NodeId(int material[]) 
    {
	nodeId = new int[nlen];
	for (int i=0; i<nlen; i++) nodeId[i] = material[i];
    }

    /**
     * Constructor.
     *
     * It constructs a new NodeId with a value of 0 for all bits.
     */
    public NodeId() 
    {
	nodeId = new int[nlen];
	for (int i=0; i<nlen; i++) nodeId[i] = 0;
    }
    
    /**
     * Blits the nodeId into a target array.
     *
     * @param target an array of length at least nodeIdBitLength/8 for the nodeId to be stored in.
     */
    
    public void blit(byte target[]) 
    {
	for (int j=0; j<nodeIdBitLength/8; j++) {
	    int k = nodeId[j / 4] >> ((j % 4) * 8);
	    target[j] = (byte)(k & 0xff);
	}
    }
    
    /**
     * Copy the nodeId into a freshly generated array.
     *
     * @return a fresh copy of the nodeId material
     */

    public byte [] copy() 
    {
	byte target[] = new byte[nodeIdBitLength/8];
	blit(target);
	return target;
    }

    /**
     * return the number of digits in a given base
     *
     * @param base the number of bits in the base
     * @return the number of digits in that base
     */ 

    public static int numDigits(int base) 
    {
	return nodeIdBitLength / base;
    }


    /**
     * Equality operator for nodeIds.
     *
     * @param obj a nodeId object
     * @return true if they are equal, false otherwise.
     */ 

    public boolean equals(Object obj) 
    {
	NodeId nid = (NodeId) obj;

	for (int i=0; i<nlen; i++) 
	    if (nodeId[i] != nid.nodeId[i]) return false;

	return true;
    }

    /**
     * Comparison operator for nodeIds.
     *
     * The comparison that occurs is a numerical comparison.
     *
     * @param obj the NodeId to compare with.
     * @return negative if this < obj, 0 if they are equal and positive if this > obj.
     */
	
    public int compareTo(Object obj) 
    {
	NodeId oth = (NodeId) obj;
	
	for (int i=nlen-1; i >= 0; i--) 
	    if (nodeId[i] != oth.nodeId[i]) {
		long t = nodeId[i] & 0x0ffffffffL;
		long o = oth.nodeId[i] & 0x0ffffffffL;
		if (t < o) return -1;
		else return 1;
	    }
	    
	return 0;
    }
    
    /**
     * Hash codes for nodeIds.
     * 
     * @return a hash code.
     */

    public int hashCode() 
    {
	int h = 0;

	/// Hash function is computed by XORing the bits of the nodeId.
	for (int i=0; i<nlen; i++)
	    h ^= nodeId[i];

	return h;		   
    }

    /**
     * A class for representing and manipulating the distance between two nodeIds on the circle.
     */
    
    public class Distance implements Comparable, Serializable {
	private int difference[];
	
	/**
	 * Constructor.
	 *
	 * @param mag the absolute magnitude of the distance.
	 */
	public Distance(int diff[]) 
	{
	    difference = diff;
	}
	
	/**
	 * Blits the distance into a target array.
	 *
	 * @param target an array of length at least nodeIdBitLength/8 for the distance to be stored in.
	 */
	public void blit(byte target[]) 
	{
	    for (int j=0; j<nodeIdBitLength/8; j++) {
		int k = difference[j / 4] >> ((j % 4) * 8);
		target[j] = (byte)(k & 0xff);
	    }
	}
    
	/**
	 * Copy the distance into a freshly generated array.
	 *
	 * @return a fresh copy of the distance material
	 */
	public byte [] copy() 
	{
	    byte target[] = new byte[nodeIdBitLength/8];
	    blit(target);
	    return target;
	}

	/**
	 * Comparison operator.
	 *
	 * The comparison that occurs is an absolute magnitude comparison.
	 *
	 * @param obj the Distance to compare with.
	 * @return negative if this < obj, 0 if they are equal and positive if this > obj.
	 */
	public int compareTo(Object obj) 
	{
	    Distance oth = (Distance) obj;

	    for (int i=nlen-1; i >= 0; i--) 
		if (difference[i] != oth.difference[i]) {
		    long t = difference[i] & 0x0ffffffffL;
		    long o = oth.difference[i] & 0x0ffffffffL;
		    if (t < o) return -1;
		    else return 1;
		}
	    
	    return 0;
	}

	/**
	 * Equality operator.
	 * 
	 * @param obj another Distance.
	 * @return true if they are the same, false otherwise.
	 */
	public boolean equals(Object obj) 
	{
	    if (compareTo(obj) == 0) return true;
	    else return false;
	}

	/**
	 * Shift operator. 
	 * shift(-1,0) multiplies value of this by two, shift(1,0) divides by 2
	 *
	 * @param cnt the number of bits to shift, negative shifts left, positive shifts right
	 * @param fill value of bit shifted in (0 if fill == 0, 1 otherwise)
	 */
	public void shift(int cnt, int fill) 
	{
	    int carry, bit;

	    if (cnt > 0) {	  
		for (int j=0; j<cnt; j++) {
		    // shift right one bit
		    carry = (fill == 0) ? 0 : 0x80000000;
		    for (int i=nlen-1; i>=0; i--) {
			bit = difference[i] & 1;
			difference[i] = (difference[i] >>> 1) | carry;
			carry = (bit == 0) ? 0 : 0x80000000;
		    }
		}
	    }
	    else {
		for (int j=0; j<-cnt; j++) {
		    // shift left one bit
		    carry = (fill == 0) ? 0 : 1;
		    for (int i=0; i<nlen; i++) {
			bit = difference[i] & 0x80000000;
			difference[i] = (difference[i] << 1) | carry;
			carry = (bit == 0) ? 0 : 1;
		    }
		}
	    }
	}

	/**
	 * Hash codes.
	 *
	 * @return a hash code.
	 */
	public int hashCode()
	{
	    int h = 0;
	    
	    // Hash function is computed by XORing the bits of the nodeId.
	    for (int i=0; i<nlen; i++)
		h ^= difference[i];
	    
	    return h;		   
	}

	/**
	 * Returns a string representation of the distance
	 *
	 * The string is a byte string from most to least significant.
	 */
	public String toString() 
	{
	    String s = "0x";
	
	    String tran[] = { "0", "1", "2", "3", "4", "5", "6", "7",
			      "8", "9", "A", "B", "C", "D", "E", "F" };
	
	    for (int j=nodeIdBitLength/8-1; j>=0; j--) {
		int k = difference[j / 4] >> ((j % 4) * 8);
		s = s + tran[(k >> 4) & 0x0f] + tran[k & 0x0f];
	    }

	    return "< nodeId.distance " + s + " >";
	}



    }
    
    /**
     * Returns the absolute numerical distance between a pair of nodeIds.
     *
     * @param nid the other node id.
     * @return an int[] containing the distance between this and nid.
     */

    private int[] absDistance(NodeId nid) 
    {
	int dist[] = new int[nlen];
	long x, y, diff;
	int carry = 0;

	if (compareTo(nid) > 0)
	    for (int i=0; i<nlen; i++) {
		x = nodeId[i] & 0x0ffffffffL;
		y = nid.nodeId[i] & 0x0ffffffffL;
		
		diff = x - y - carry;
		
		if (diff < 0) carry = 1;
		else carry = 0;

		dist[i] = (int)diff;
	    }
	else 
	    for (int i=0; i<nlen; i++) {
		x = nodeId[i] & 0x0ffffffffL;
		y = nid.nodeId[i] & 0x0ffffffffL;
		
		diff = y - x - carry;
		
		if (diff < 0) carry = 1;
		else carry = 0;

		dist[i] = (int)diff;
	    }	       

	//System.out.println("absDist=" + new Distance(dist));
	return dist;
    }

    /**
     * Returns the shorter numerical distance on the ring between a pair of nodeIds.
     *
     * @param nid the other node id.
     * @return the distance between this and nid.
     */

    public Distance distance(NodeId nid) 
    {
	int[] dist = absDistance(nid);

	if ( (dist[nlen-1] & 0x80000000) != 0 ) 
	    invert(dist);

	Distance d = new Distance(dist);
	//System.out.println("Dist:" + this + nid + d); 

	return d;
    }

    /**
     * Returns the longer numerical distance on the ring between a pair of nodeIds.
     *
     * @param nid the other node id.
     * @return the distance between this and nid.
     */

    public Distance longDistance(NodeId nid) 
    {
	int[] dist = absDistance(nid);

	if ( (dist[nlen-1] & 0x80000000) == 0 )
	    invert(dist);

	Distance d = new Distance(dist);
	//System.out.println("Diff:" + this + nid + d); 

	return d;
    }

    /**
     * inverts the distance value stored in an integer array (computes 0-value)
     *
     * @param dist the distance value
     */
    private void invert(int[] dist) {
	int carry = 0;
	long diff;
	for (int i=0; i<nlen; i++) {
	    diff = dist[i] & 0x0ffffffffL;
	    diff = 0L - diff - carry;
	    if (diff < 0) carry = 1;
	    dist[i] = (int)diff;
	}
    }


    /**
     * Xor operator for nodeIds.
     * Sets this nodeId to the bit-wise XOR of itself and otherId
     *
     * @param otherId a nodeId object
     */ 

    public void xor(NodeId otherId ) 
    {
	for (int i=0; i<nlen; i++) 
	    nodeId[i] ^= otherId.nodeId[i];
    }


    /**
     * Equivalence relation for nodeIds.
     *
     * @param nid the other node id.
     * @return true if they are equal, false otherwise.
     */
    
    public boolean equals(NodeId nid) {
	if(nid == null) return false;

	for (int i=0; i<nlen; i++)
	    if (nodeId[i] != nid.nodeId[i]) return false;
	
	return true;	
    }

    /**
     * Checks to see if the nodeId nid is clockwise or counterclockwise on the ring.
     *
     * @return true if clockwise, false otherwise.
     */
    
    public boolean clockwise(NodeId nid) 
    {
	boolean diffMSB = ((nodeId[nlen-1] & 0x80000000) != (nid.nodeId[nlen-1] & 0x80000000));
	int x, y;	
	int i;

	if ((x = (nodeId[nlen-1] & 0x7fffffff)) != (y = (nid.nodeId[nlen-1] & 0x7fffffff))) {
	    return ((y > x) ^ diffMSB);
	}
	else {
	    for (i = nlen-2; i >= 0; i--) 
		if (nodeId[i] != nid.nodeId[i]) break;
	    
	    if (i < 0) 
		return diffMSB;
	    else {
		long xl, yl;

		xl = nodeId[i] & 0xffffffffL;
		yl = nid.nodeId[i] & 0xffffffffL;

		return ((yl > xl) ^ diffMSB);
	    }
	}
    }

    /**
     * Checks if the ith bit is flipped.
     *
     * i = 0 is the least significant bit.
     *
     * @param i which bit to check.
     *
     * @return true if the bit is set, false otherwise.
     */

    public boolean checkBit(int i) 
    {
	int index = i / 32;
	int shift = i % 32;
	int val = nodeId[index];
	int mask = (1 << shift);

	if ((val & mask) != 0) return true;
	else return false;
    }


    /**
     * Sets the ith bit to a given value
     *
     * i = 0 is the least significant bit.
     *
     * @param i which bit to set.
     * @param v new value of bit
     *
     */

    public void setBit(int i, int v) 
    {
	int index = i / 32;
	int shift = i % 32;
	int val = nodeId[index];
	int mask = (1 << shift);
	
	if (v == 1)
	    nodeId[index] = val | mask;
	else
	    nodeId[index] = val & ~mask;
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

    public int getDigit(int i, int b) 
    {
	int bitIndex = b * i + (nodeIdBitLength % b);
	int index = bitIndex / 32;
	int shift = bitIndex % 32;

	long val = nodeId[index];
	if (shift + b > 32) val = (val & 0xffffffffL) | (((long)nodeId[index+1]) << 32);
	//System.out.println("val=" + Long.toHexString(val));}
	return ((int)(val >> shift)) & ((1 << b) - 1);
    }


    /**
     * Sets the ith digit in base 2^b.
     *
     * i = 0 is the least significant digit.
     * 
     * @param i which digit to get.
     * @param v the new value of the digit
     * @param b which power of 2 is the base to get it in.
     */

    public void setDigit(int i, int v, int b) 
    {
	int bitIndex = b * i + (nodeIdBitLength % b);
	int index = bitIndex / 32;
	int shift = bitIndex % 32;
	int mask = (1 << b) - 1;

	if (shift + b > 32) {
	    // digit overlaps a word boundary

	    long newd = ((long)(v & mask)) << shift;
	    long vmask  = ~(((long)mask) << shift);
	    long val = nodeId[index];
	    val = (val & 0xffffffffL) | (((long)nodeId[index+1]) << 32);

	    val = (val & vmask) | newd;

	    nodeId[index] = (int)val;
	    nodeId[index+1] = (int)(val >> 32);
	} else {
	    int newd = (v & mask) << shift;
	    int vmask = ~(mask << shift);
	    nodeId[index] = (nodeId[index] & vmask) | newd;
	}
    }

    /**
     * Returns the index of the most significant differing bit (MSDB).
     *
     * @param nid another node id to compare with.
     * @return the index of the msdb (0 is the least significant) / will return negative if they do not differ.
     */
    
    public int indexOfMSDB(NodeId nid)
    {
	for (int i=nlen-1; i>=0; i--) {
	    int cmp = nodeId[i] ^ nid.nodeId[i];
	    
	    if (cmp != 0) {
		int tmp;
		int j = 0;
		if ((tmp = cmp & 0xffff0000) != 0) {cmp = tmp; j += 16;}
		if ((tmp = cmp & 0xff00ff00) != 0) {cmp = tmp; j += 8;}
		if ((tmp = cmp & 0xf0f0f0f0) != 0) {cmp = tmp; j += 4;}
		if ((tmp = cmp & 0xcccccccc) != 0) {cmp = tmp; j += 2;}
		if ((tmp = cmp & 0xaaaaaaaa) != 0) {cmp = tmp; j += 1;}
		return 32 * i + j;
	    }
	}

	return -1;
    }    
    
    /**
     * Returns the index of the most significant different digit (MSDD) in a given base.
     *
     * @param nid another node id to compare with.
     * @param base the base (as a power of two) to compare in.
     *
     * @return the index of the msdd (0 is the least significant) / will return negative if they do not differ.
     */

    public int indexOfMSDD(NodeId nid, int base) 
    {
	int ind = indexOfMSDB(nid);

	// ignore trailing LSBs if (nodeIdBitLength % base) > 0
	ind -= nodeIdBitLength % base;

	if (ind < 0) return ind;
	return ind / base;
    }

    /**
     * produces a nodeId whose prefix up to row is identical to this, followed by a digit with value 
     * column, followed by a suffix of digits with value suffixDigits.
     *
     * @param row the length of the prefix
     * @param column the value of the following digit
     * @param suffixDigit the value of the suffix digits
     * @param b power of 2 of the base
     * @return the resulting nodeId
     */

    public NodeId getDomainPrefix(int row, int column, int suffixDigit, int b) {
	NodeId res = new NodeId(nodeId);
	
	res.setDigit(row, column, b);
	for (int i=0; i<row; i++)
	    res.setDigit(i, suffixDigit, b);

	return res;
    }


    /**
     * produces a set of ids (keys) that are evenly distributed around
     * the id ring.  One invocation produces the i-th member of a set
     * of size num. The set is evenly distributed around the ring,
     * with an offset given by this nodeId.  The set is useful for
     * constructing, for instance, Scribe trees with disjoint sets of
     * interior nodes.
     * @param num the number of nodeIds in the set (must be <= 2^b)
     * @param b the routing base (as a power of 2)
     * @param i the index of the requested member of the set (0<=i<num; the 0-th member is this)
     * @return the resulting set member, or null in case of illegal arguments
     */

    public NodeId getAlternateId(int num, int b, int i) {
	if (num > (1 << b) || i < 0 || i >= num) return null;

	NodeId res = new NodeId(nodeId);

	int digit = res.getDigit(numDigits(b)-1, b) + ((1 << b) / num) * i;
	res.setDigit(numDigits(b)-1, digit, b);

	return res;
    }



    /**
     * Creates a random nodeId. For testing purposed only -- should
     * NOT be used to generate real node or object identifiers (low
     * quality of random material).
     *
     * @param rng random number generator
     * @return a random nodeId 
     */

    public static NodeId makeRandomId(Random rng) {
	byte material[] = new byte[nodeIdBitLength/8];
	rng.nextBytes(material);
	return new NodeId(material);
    }


    /**
     * Returns a string representation of the nodeId in base 16.
     *
     * The string is a byte string from most to least significant.
     */

    public String toString() 
    {
	String s = "0x";
	
	String tran[] = { "0", "1", "2", "3", "4", "5", "6", "7",
			  "8", "9", "A", "B", "C", "D", "E", "F" };
	
	int n = nodeIdBitLength / 4;

	for (int i=n-1; i>=0; i--) {
	    int d = getDigit(i, 4);
	    	    
	    s = s + tran[d];
	}

	return "<" + s.substring(0,6) + "..>";
	//return "<" + s.substring(0,5)+"..." + ">";
	//return "< nodeId " + s + " >";
    }


}


