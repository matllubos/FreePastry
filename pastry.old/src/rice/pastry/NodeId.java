//////////////////////////////////////////////////////////////////////////////
// Rice Open Source Pastry Implementation                  //               //
//                                                         //  R I C E      //
// Copyright (c)                                           //               //
// Romer Gil                   rgil@cs.rice.edu            //   UNIVERSITY  //
// Andrew Ladd                 aladd@cs.rice.edu           //               //
// Tsuen Wan Ngan              twngan@cs.rice.edu          ///////////////////
//                                                                          //
// This program is free software; you can redistribute it and/or            //
// modify it under the terms of the GNU General Public License              //
// as published by the Free Software Foundation; either version 2           //
// of the License, or (at your option) any later version.                   //
//                                                                          //
// This program is distributed in the hope that it will be useful,          //
// but WITHOUT ANY WARRANTY; without even the implied warranty of           //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the            //
// GNU General Public License for more details.                             //
//                                                                          //
// You should have received a copy of the GNU General Public License        //
// along with this program; if not, write to the Free Software              //
// Foundation, Inc., 59 Temple Place - Suite 330,                           //
// Boston, MA  02111-1307, USA.                                             //
//                                                                          //
// This license has been added in concordance with the developer rights     //
// for non-commercial and research distribution granted by Rice University  //
// software and patent policy 333-99.  This notice may not be removed.      //
//////////////////////////////////////////////////////////////////////////////

package rice.pastry;

import java.lang.Comparable;
import java.io.*;

/**
 * A single node identifier and the bit length for nodes is stored in this class.
 * nodeIds are stored big endian.
 *
 * @author Andrew Ladd
 */

public class NodeId implements Serializable 
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
	    h ^= (nodeId[i] << (i % 4));

	return h;		   
    }

    /**
     * A class for representing and manipulating the distance between two nodeIds on the circle.
     */
    
    public class Distance implements Comparable {
	private byte magnitude[];
	
	/**
	 * Constructor.
	 *
	 * @param mag the absolute magnitude of the distance.
	 */

	public Distance(byte mag[]) 
	{
	    magnitude = mag;
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

	    if (magnitude.length < oth.magnitude.length) return -1;
	    if (magnitude.length > oth.magnitude.length) return 1;
	    
	    for (int i=0; i<magnitude.length; i++) 
		if (magnitude[i] != oth.magnitude[i]) {
		    int x = magnitude[i];
		    int y = oth.magnitude[i];
		    
		    if (x < 0) x += 256;
		    if (y < 0) y += 256;
		    
		    if (x < y) return -1;
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
    }    
    
    /**
     * Returns the distance between a pair of nodeIds.
     *
     * @param nid the other node id.
     * @return the distance between this and nid.
     */

    public Distance distance(NodeId nid) 
    {
	int n = nodeIdBitLength >> 3;
	
	byte mag[] = new byte[n];

	for (int i=0; i<n; i++) {
	    int x = nodeId[i];
	    int y = nid.nodeId[i];

	    mag[i] = (byte) (x ^ y);
	}

	return new Distance(mag);
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

	int val = nodeId[i];

	if (val < 0) val += 256;

	int mask = (1 << shift);

	if ((val & mask) != 0) return true;
	else return false;
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
     * Returns the index of the most significant differing bit (MSDB).
     *
     * @param nid another node id to compare with.
     * @return the index of the msdb (0 is the least significant) / will return negative if they do not differ.
     */
    
    public int indexOfMSDB(NodeId nid)
    {
	int n = nodeIdBitLength >> 3;

	for (int i=0; i<n; i++) {
	    int x = nodeId[i];
	    int y = nid.nodeId[i];

	    if (x < 0) x += 256;
	    if (y < 0) y += 256;

	    int cmp = (x ^ y);
	    
	    if (cmp != 0) {
		int mask = 0x80;
		
		for (int j=0; j<8; j++) {
		    if ((cmp & mask) != 0) return nodeIdBitLength - 8 * i + j - 1;
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
	
	for (int i=0; i<nodeIdBitLength; i++) {
	    int iden = nodeId[i];	    
	    int l = nodeId[i] & 0x0F;
	    int h = (nodeId[i] >> 4) & 0x0F;
	    
	    s = s + tran[h] + tran[l];
	}

	return "< nodeId " + s + " >";
    }

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
}


