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
 * @version $Id$
 *
 * @author Andrew Ladd
 * @author Peter Druschel
 */

public class RoutingTable extends Observable implements Observer {
    /**
     * The routing calculations will occur in base <EM> 2 <SUP> idBaseBitLength </SUP> </EM>
     */
    
    public static int idBaseBitLength = 4;

    private NodeId myNodeId;
    private NodeHandle myNodeHandle;
    private RouteSet routingTable[][];
    
    private int maxEntries;
    
    /**
     * Constructor.
     *
     * @param me the node id for this routing table.
     * @param max the maximum number of entries at each table slot.
     */

    public RoutingTable(NodeHandle me, int max) {
	myNodeId = me.getNodeId();
	myNodeHandle = me;
	maxEntries = max;
	
	int cols = 1 << idBaseBitLength;
	int rows = NodeId.nodeIdBitLength / idBaseBitLength;

	routingTable = new RouteSet[rows][cols];

	for (int i=0; i<rows; i++) {
	    int myCol = myNodeId.getDigit(i,idBaseBitLength);
	    // insert this node at the appropriate column
	    routingTable[i][myCol] = new RouteSet(maxEntries);
	    routingTable[i][myCol].put(myNodeHandle);
	    routingTable[i][myCol].addObserver(this);
	}
    }

    /**
     * return ths number of columns in the routing table
     *
     * @return number of columns
     */

    public int numColumns() { return routingTable[0].length; }

    /**
     * return the number of rows in the routing table
     *
     * @return number of rows
     */

    public int numRows() { return routingTable.length; }

    /**
     * return the bit length of the base
     *
     * @return baseBitLength
     */

    public static int baseBitLength() { return idBaseBitLength; }


    /**
     * Determines an alternate hop numerically closer to the key than the one we are at. This assumes
     * that bestEntry did not produce a live nodeHandle that matches the next digit of the key.
     * 
     * @param key the key
     * @return a nodeHandle of a numerically closer node, relative to the key
     */

    public NodeHandle bestAlternateRoute(NodeId key)
    {
	final int cols = 1 << idBaseBitLength;
	int diffDigit = myNodeId.indexOfMSDD(key, idBaseBitLength);
	if (diffDigit < 0) return null;
	int keyDigit = key.getDigit(diffDigit, idBaseBitLength);
	int myDigit = myNodeId.getDigit(diffDigit, idBaseBitLength);
	NodeId.Distance bestDistance = myNodeId.distance(key);
	NodeHandle alt = null;
	boolean finished = false;

	for (int i=1; !finished; i++) {
	    for (int j=0; j<2; j++) {
		int digit = (j == 0) ? 
		    (keyDigit + i) & (cols - 1) : (keyDigit + cols - i) & (cols - 1);

		RouteSet rs = getRouteSet(diffDigit, digit);
		for (int k=0; rs!=null && k<rs.size(); k++) {
		    NodeHandle n = rs.get(k);
		    
		    if (n.isAlive()) {
			NodeId.Distance nDist = n.getNodeId().distance(key);

			if (bestDistance.compareTo(nDist) > 0) {
			    bestDistance = nDist;
			    alt = n;
			}
		    }
		}
		
		if (digit == myDigit) finished = true;
	    }
	}
	
	/*
	if (alt != null) {
	    System.out.println("RT:bestAlternateRoute, key=" + key +
	                       " alternate=" + alt.getNodeId());
	}
	*/

	return alt;
    }

    /**
     * Determines a set of alternate hops towards a given key.
     * 
     * @param key the key
     * @param set a set of nodehandles (result)
     * @param max the maximal number of alternate hops requested
     */

    public void alternateRoutes(Id key, NodeSet set, int max)
    {
	final int cols = 1 << idBaseBitLength;
	int diffDigit = myNodeId.indexOfMSDD(key, idBaseBitLength);
	if (diffDigit < 0) return;
	int keyDigit = key.getDigit(diffDigit, idBaseBitLength);
	int myDigit = myNodeId.getDigit(diffDigit, idBaseBitLength);
	Id.Distance myDistance = myNodeId.distance(key);
	boolean finished = false;
	int count = 0;

	for (int i=0; !finished; i++) {
	    for (int j=0; j<2; j++) {
		int digit = (j == 0) ? 
		    (keyDigit + i) & (cols - 1) : (keyDigit + cols - i) & (cols - 1);

		RouteSet rs = getRouteSet(diffDigit, digit);
		for (int k=0; rs!=null && k<rs.size(); k++) {
		    NodeHandle n = rs.get(k);
		    
		    if (n.isAlive()) {
			Id.Distance nDist = n.getNodeId().distance(key);

			if (set != null && count < max && myDistance.compareTo(nDist) > 0) {
			    set.put(n);
			    count++;
			}
		    }
		}
		
		if (digit == myDigit) finished = true;
	    }
	}
    }

    /**
     * Gets the set of handles at a particular entry in the table.
     *
     * @param index the index of the digit in base <EM> 2 <SUP> idBaseBitLength </SUP></EM>.  <EM> 0 </EM> is the least significant.
     * @param digit ranges from <EM> 0... 2 <SUP> idBaseBitLength - 1 </SUP> </EM>.  Selects which digit to use.
     *
     * @return a read-only set of possible handles located at that position in the routing table, or null if none are known
     */

    public RouteSet getRouteSet(int index, int digit) 
    {
	RouteSet ns = routingTable[index][digit];

	return ns;
    }

    /**
     * Gets the set of handles that match at least one more digit of the key than the local nodeId.
     *
     * @param key the key
     *
     * @return a read-only set of possible handles, or null if none are known
     */
    
    public RouteSet getBestEntry(NodeId key) 
    {
	int diffDigit = myNodeId.indexOfMSDD(key, idBaseBitLength);
	if (diffDigit < 0) return null;
	int digit = key.getDigit(diffDigit, idBaseBitLength);

	return routingTable[diffDigit][digit];
    }


    /**
     * Like getBestEntry, but creates an entry if none currently exists.
     *
     * @param key the key
     *
     * @return a read-only set of possible handles
     */
    
    private RouteSet makeBestEntry(NodeId key) 
    {
	int diffDigit = myNodeId.indexOfMSDD(key, idBaseBitLength);
	if (diffDigit < 0) return null;
	int digit = key.getDigit(diffDigit, idBaseBitLength);

	if (routingTable[diffDigit][digit] == null) {
	    // allocate a RouteSet
	    routingTable[diffDigit][digit] = new RouteSet(maxEntries);
	    routingTable[diffDigit][digit].addObserver(this);
	}

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
	RouteSet ns = makeBestEntry(nid);

	if (ns != null) ns.put(handle);
    }

    /**
     * Gets the node handle associated with a given id.
     *
     * @param nid a node id
     * @return the handle associated with that id, or null if none is known.
     */
    
    public NodeHandle get(NodeId nid) 
    {
	RouteSet ns = getBestEntry(nid);

	if (ns == null) return null;
	
	return ns.get(nid);
    }

    /**
     * Get a row from the routing table.
     *
     * @param i which row
     * @return an array which is the ith row.
     */

    public RouteSet[] getRow(int i) { return routingTable[i]; }
    
    /**
     * Removes a node id from the table.
     *
     * @param nid the node id to remove.
     * @return the handle that was removed, or null if it did not exist.
     */
    
    public NodeHandle remove(NodeId nid) 
    {
	RouteSet ns = getBestEntry(nid);
	
	if (ns == null) return null;

	return ns.remove(nid);
    }


    /**
     * Is called by the Observer pattern whenever a RouteSet in this table
     * has changed.
     *
     * @param o the RouteSet
     * @param arg the event 
     */
    public void update(Observable o, Object arg) {
	// pass the event to the Observers of this RoutingTable
	setChanged();
	notifyObservers(arg);
    }

    /**
     * produces a String representation of the routing table, showing the number of node handles in each entry
     *
     */

    public String toString() 
    {
	String s = "routing table: \n";

	for (int i=routingTable.length-1; i>=0; i--) {
	    for (int j=0; j<routingTable[i].length; j++) {
		if (routingTable[i][j] != null)
		    s += ("" + routingTable[i][j].size() + "\t");
		else
		    s += ("" + 0 + "\t");
	    }		
	    s += ("\n");
	}
	
	return s;
    }
}
