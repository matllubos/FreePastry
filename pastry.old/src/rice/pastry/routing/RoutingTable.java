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

package rice.pastry.routing;

import rice.pastry.*;

import java.util.*;

/**
 * The Pastry routing table. 
 * <P>
 * The size of this table is determined by two constants:
 * <P> <UL>
 * <LI> {@link rice.pastry.NodeId#nodeIdBitLength nodeIdBitLength}  which determines the number of bits in a node id (which we call <EM> n </EM>).
 * <LI> {@link RoutingTable#idBaseBitLength idBaseBitLength} which is the base that table is stored in (which we call <EM> b </EM>).
 * </UL>
 * <P>
 * We write out node ids as numbers in base <EM> 2 <SUP> b </SUP> </EM>.  
 * They will have length <EM> D = ceiling(log <SUB> 2 <SUP> b </SUP> </SUB> 2 <SUP> n </SUP>) </EM>.
 * The table is stored from <EM> 0...(D-1) </EM> by <EM> 0...(2 <SUP> b </SUP> - 1)</EM>.  
 * The table stores a set of node handles at each entry.
 * At address <EM> [index][digit] </EM>, we store the set of handles were 
 * the most significant (numerically) difference from the node id that the table
 * routes for at the <EM> index </EM>th digit and the differing digit is <EM> digit </EM>.  An <EM> index </EM> of <EM> 0 </EM>
 *  is the least significant digit.
 *
 * @author Andrew Ladd
 */

public class RoutingTable implements NodeSet {
    /**
     * The routing calculations will occur in base <EM> 2 <SUP> idBaseBitLength </SUP> </EM>
     */
    
    public final static int idBaseBitLength = 4;

    private NodeId myNodeId;
    private NeighbourSet routingTable[][];
    
    private int maxEntries;
    
    /**
     * Constructor.
     *
     * @param me the node id for this routing table.
     * @param max the maximum number of entries at each table slot.
     */

    public RoutingTable(NodeId me, int max) {
	myNodeId = me;
	maxEntries = max;
	
	int cols = 1 << idBaseBitLength;
	int rows = NodeId.nodeIdBitLength / idBaseBitLength;

	if (NodeId.nodeIdBitLength % idBaseBitLength > 0) rows++;
	
	routingTable = new NeighbourSet[rows][cols];

	for (int i=0; i<rows; i++)
	    for (int j=0; j<cols; j++) routingTable[i][j] = new NeighbourSet(myNodeId, maxEntries);
    }

    /**
     * Determines a hop strictly better than the one we are at.
     *
     * @param nextId the destination node id.
     * @return a set of possible handles to the next hop or null if no strictly better hop can be found.
     */

    public NeighbourSet bestRoute(NodeId nextId)
    {
	int diffDigit = myNodeId.indexOfMSDD(nextId, idBaseBitLength);

	if (diffDigit < 0) return null;
	
	int digit = nextId.getDigit(diffDigit, idBaseBitLength);
	
	return getNodeSet(diffDigit, digit);
    }

    /**
     * Gets the set of handles at a particular entry in the table.
     *
     * @param index the index of the digit in base <EM> 2 <SUP> idBaseBitLength </SUP></EM>.  <EM> 0 </EM> is the least significant.
     * @param digit ranges from <EM> 0... 2 <SUP> idBaseBitLength - 1 </SUP> </EM>.  Selects which digit to use.
     *
     * @return a read-only set of possible handles located at that position in the routing table or null if this empty.
     */

    public NeighbourSet getNodeSet(int index, int digit) 
    {
	NeighbourSet ns = routingTable[index][digit];

	if (ns.size() == 0) return null;

	return ns;
    }
    
    private NeighbourSet getBestEntry(NodeId nid) 
    {
	int diffDigit = myNodeId.indexOfMSDD(nid, idBaseBitLength);

	if (diffDigit < 0) return null;
	
	int digit = nid.getDigit(diffDigit, idBaseBitLength);

	return routingTable[diffDigit][digit];
    }

    /**
     * Puts a handle into the routing table.
     *
     * @param handle the handle to put.
     */

    public void put(NodeHandle handle) 
    {
	NodeId nid = handle.getNodeId();

	NeighbourSet ns = getBestEntry(nid);

	ns.put(handle);
    }

    /**
     * Gets the node handle associated with a given id.
     *
     * @param nid a node id
     * @return the handle associated with that id.
     */
    
    public NodeHandle get(NodeId nid) 
    {
	NeighbourSet ns = getBestEntry(nid);

	return ns.get(nid);
    }
    
    /**
     * Removes a node id from the table.
     *
     * @param nid the node id to remove.
     * @return the handle that was removed.
     */
    
    public NodeHandle remove(NodeId nid) 
    {
	NeighbourSet ns = getBestEntry(nid);

	return ns.remove(nid);
    }

    private class MyIterator implements Iterator {
	private int i, j;
	private Iterator iter;
	private Iterator last;
	
	private void advance() 
	{
	    if (iter == null && j < routingTable[0].length) 
		iter = routingTable[i][j].closestIterator();

	    if (iter.hasNext() == false) 
		{
		    iter = null;
		    i++;
		    
		    if (i == routingTable.length) { j++; i = 0; }
		    
		    advance();
		}	    
	}

	public MyIterator() {
	    i = 0;
	    j = 0;

	    iter = null;
	    last = null;
	    
	    advance();
	}
	
	public boolean hasNext() 
	{
	    return j == routingTable[0].length;
	}
	
	public Object next() 
	{
	    last = iter;
	    
	    return iter.next();
	}

	public void remove() 
	{
	    last.remove();
	}
    }

    /**
     * Returns an iterator over the RoutingTable.
     *
     * @return an iterator.
     */

    public Iterator iterator() { return new MyIterator(); }

    public String toString() 
    {
	String s = "";

	for (int i=0; i<routingTable.length; i++) {
	    for (int j=0; j<routingTable[i].length; j++) {
		s += ("" + routingTable[i][j].size() + "\t");
	    }		
	    s += ("\n");
	}
	
	return s;
    }
}
