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
     * that it is divisible by 8.
     */
    
    public final static int nodeIdBitLength = 128;
    
    /// 2^(nodeIdBitLength - 3) elements in the array.
    private byte nodeId[];

    /**
     * Constructor.
     *
     * @param material an array of length at least 2^(nodeIdBitLength - 3) containing raw nodeId material.
     */
    public NodeId(byte material[]) 
    {
	int n = nodeIdBitLength >> 3;
	
	nodeId = new byte[n];

	for (int i=0; i<n; i++) nodeId[i] = material[i];
    }

    /**
     * Blits the nodeId into a target array.
     *
     * @param target an array of length at least 2^(nodeIdBitLength - 3) for the nodeId to be stored in.
     */
    
    public void blit(byte target[]) 
    {
	int n = nodeIdBitLength >> 3;
	
	for (int i=0; i<n; i++) target[i] = nodeId[i];
    }
    
    /**
     * Copy the nodeId into a freshly generated array.
     *
     * @return a fresh copy of the nodeId material
     */

    public byte [] copy() 
    {
	int n = nodeIdBitLength >> 3;

	byte target[] = new byte[n];

	for (int i=0; i<n; i++) target[i] = nodeId[i];
	
	return target;
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
	
	int n = nodeIdBitLength >> 3;

	for (int i=0; i<n; i++) 
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
	
	for (int i=nodeId.length - 1; i >= 0; i--) 
	    if (nodeId[i] != oth.nodeId[i]) {
		int t = nodeId[i] & 0xff;
		int o = oth.nodeId[i] & 0xff;
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
	int n = nodeIdBitLength >> 3;

	int h = 0;

	/// Hash function is computed by cyclicly XORing the bits of the nodeId.
	
	for (int i=0; i<n; i++)
	    h ^= (nodeId[i] << (i % 24));

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

	    if (difference.length < oth.difference.length) return -1;
	    if (difference.length > oth.difference.length) return 1;
	    
	    for (int i=difference.length - 1; i >= 0; i--) 
		if (difference[i] != oth.difference[i]) {
		    if (difference[i] < oth.difference[i]) return -1;
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
	 * Hash codes.
	 *
	 * @return a hash code.
	 */
	
	public int hashCode()
	{
	    int n = nodeIdBitLength >> 3;
	    
	    int h = 0;
	    
	    // Hash function is computed by cyclicly XORing the bits of the nodeId.
	    
	    for (int i=0; i<n; i++)
		h ^= (difference[i] << (i % 24));
	    
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
	
	    int n = nodeIdBitLength >> 3;

	    for (int i=n-1; i>=0; i--) {
		s = s + tran[(difference[i] >> 4) & 0x0f] + tran[difference[i] & 0x0f];
	    }

	    return "< nodeId.distance " + s + " >";
	}



    }
    
    /**
     * Returns the shorter numerical distance on the ring between a pair of nodeIds.
     *
     * @param nid the other node id.
     * @return the distance between this and nid.
     */

    public Distance distance(NodeId nid) 
    {
	int n = nodeIdBitLength >> 3;
	
	int diff[] = new int[n];
	int x, y;
	int carry = 0;

	// if (clockwise(nid)) 
	if (compareTo(nid) > 0)
	    for (int i=0; i<n; i++) {
		x = nodeId[i];
		y = nid.nodeId[i];
		
		if (x < 0) x+=256;
		if (y < 0) y+=256;

		diff[i] = x - y - carry;
		
		if (diff[i] < 0) {
		    diff[i] += 256;
		    carry = 1;
		}
		else carry = 0;
	    }
	else 
	    for (int i=0; i<n; i++) {
		x = nodeId[i];
		y = nid.nodeId[i];
		
		if (x < 0) x+=256;
		if (y < 0) y+=256;

		diff[i] = y - x - carry;
		
		if (diff[i] < 0) {
		    diff[i] += 256;
		    carry = 1;
		}
		else carry = 0;
	    }	       

	if ( (diff[n-1] & 0x80) != 0 ) {
	    carry = 0;
	    for (int i=0; i<n; i++) {
		diff[i] = 0 - diff[i] - carry;
		if (diff[i] < 0) {
		    diff[i] += 256;
		    carry = 1;
		}
	    }
	}

	Distance d = new Distance(diff);
	//System.out.println("Diff:" + this + nid + d); 

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
	int n = nodeIdBitLength >> 3;
	
	int diff[] = new int[n];
	int x, y;
	int carry = 0;

	// if (clockwise(nid)) 
	if (compareTo(nid) > 0)
	    for (int i=0; i<n; i++) {
		x = nodeId[i];
		y = nid.nodeId[i];
		
		if (x < 0) x+=256;
		if (y < 0) y+=256;

		diff[i] = x - y - carry;
		
		if (diff[i] < 0) {
		    diff[i] += 256;
		    carry = 1;
		}
		else carry = 0;
	    }
	else 
	    for (int i=0; i<n; i++) {
		x = nodeId[i];
		y = nid.nodeId[i];
		
		if (x < 0) x+=256;
		if (y < 0) y+=256;

		diff[i] = y - x - carry;
		
		if (diff[i] < 0) {
		    diff[i] += 256;
		    carry = 1;
		}
		else carry = 0;
	    }	       

	if ( (diff[n-1] & 0x80) == 0 ) {
	    carry = 0;
	    for (int i=0; i<n; i++) {
		diff[i] = 0 - diff[i] - carry;
		if (diff[i] < 0) {
		    diff[i] += 256;
		    carry = 1;
		}
	    }
	}

	Distance d = new Distance(diff);
	//System.out.println("Diff:" + this + nid + d); 

	return d;
    }


    /**
     * Equivalence relation for nodeIds.
     *
     * @param nid the other node id.
     * @return true if they are equal, false otherwise.
     */
    
    public boolean equals(NodeId nid) {
	int n = nodeIdBitLength >> 3;

	for (int i=0; i<n; i++)
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
	int n = nodeIdBitLength >> 3;

	boolean diffMSB = ((nodeId[n - 1] & 0x80) != (nid.nodeId[n-1] & 0x80));
	int i = n - 1;
	
	if ((nodeId[i] & 0x7f) == (nid.nodeId[i] & 0x7f)) 
	    for (i = n - 2; i >= 0; i--) 
		if (nodeId[i] != nid.nodeId[i]) break;
	
	if (i >= 0) {
	    int x, y;
	    x = (nodeId[i] < 0 ? (nodeId[i] + 256) : nodeId[i]);
	    y = (nid.nodeId[i] < 0 ? (nid.nodeId[i] + 256) : nid.nodeId[i]);

	    if (i == n - 1) {
		x &= 0x7f;
		y &= 0x7f;
	    }
	    
	    return ((y > x) ^ diffMSB);
	}

	return diffMSB;
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
	int index = i / 8;
	int shift = i % 8;
	int val = nodeId[index];
	if (val < 0) val += 256;
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
	int index = i / 8;
	int shift = i % 8;
	int val = nodeId[index];
	if (val < 0) val += 256;
	int mask = (1 << shift);
	
	if (v == 1)
	    nodeId[index] = (byte)(val | mask);
	else
	    nodeId[index] = (byte)(val & ~mask);
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
	int base = 1 << b;
	int index = b * i;
	int val = 0;
	int mask = 1;

	for (int j=0; j<b; j++) {
	    if (checkBit(j + index) == true) val += mask;
	    mask <<= 1;
	}

	return val;
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
	int base = 1 << b;
	int index = b * i;
	int mask = 1;

	for (int j=0; j<b; j++) {
	    setBit(j + index, v & 1);
	    v >>= 1;
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
	int n = nodeIdBitLength >> 3;

	for (int i=n - 1; i>=0; i--) {
	    int x = nodeId[i];
	    int y = nid.nodeId[i];

	    if (x < 0) x += 256;
	    if (y < 0) y += 256;

	    int cmp = (x ^ y);
	    
	    if (cmp != 0) {
		int mask = 0x80;
		
		for (int j=0; j<8; j++) {
		    if ((cmp & mask) != 0) return 8 * i + 7 - j;
		    mask >>= 1;
		}
	    }
	}

	return -1;
    }    
    
    /**
     * Returns the index of the most significant different digit (MSDD) in a given base.
     *
     * @param nid another node id to compare with.
     * @param base the base to compare in.
     *
     * @return the index of the msdd (0 is the least significant) / will return negative if they do not differ.
     */

    public int indexOfMSDD(NodeId nid, int base) 
    {
	int ind = indexOfMSDB(nid);

	if (ind < 0) return ind;

	return ind / base;
    }

    public NodeId getDomainPrefix(int row, int column, int suffixDigit) {
	NodeId res = new NodeId(nodeId);
	
	res.setDigit(row, column, 4);
	for (int i=0; i<row; i++)
	    res.setDigit(i, suffixDigit, 4);

	return res;
    }


    /**
     * Creates a random nodeId.
     *
     * @param rng random number generator
     * @return a random nodeId
     */
    public static NodeId makeRandomId(Random rng) {
	byte material[] = new byte[nodeIdBitLength >> 3];
	rng.nextBytes(material);
	return new NodeId(material);
    }


    /**
     * Returns a string representation of the nodeId.
     *
     * The string is a byte string from most to least significant.
     */

    public String toString() 
    {
	String s = "0x";
	
	String tran[] = { "0", "1", "2", "3", "4", "5", "6", "7",
			  "8", "9", "A", "B", "C", "D", "E", "F" };
	
	int n = nodeIdBitLength >> 2;

	for (int i=n-1; i>=0; i--) {
	    int d = getDigit(i, 4);
	    	    
	    s = s + tran[d];
	}

	return "<" + s.substring(0,6) + "..>";
	//return "<" + s.substring(0,5)+"..." + ">";
	//return "< nodeId " + s + " >";
    }

    /*
    private void readObject(ObjectInputStream in)
	throws IOException, ClassNotFoundException 
    {
	int n = nodeIdBitLength >> 3;
	
	nodeId = new byte[n];
	
	for (int i=0; i<n; i++) 
	    nodeId[i] = in.readByte();
    }

    private void writeObject(ObjectOutputStream out)
	throws IOException, ClassNotFoundException 
    {
	int n = nodeIdBitLength >> 3;

	for (int i=0; i<n; i++) out.writeByte(nodeId[i]);
    } 
    */

}


