package rice.pastry.leafset;

import rice.pastry.*;

import java.util.*;
import java.io.*;

public class SimilarSet extends Observable implements NodeSet, Serializable
{
    private NodeId baseId;
    private boolean clockwise;

    private NodeHandle[] nodes;
    private NodeId.Distance[] dist;

    private int theSize;

    protected void swap(int i, int j) {
	NodeHandle handle = nodes[i];
	NodeId.Distance d = dist[i];

	nodes[i] = nodes[j];
	dist[i] = dist[j];
	
	nodes[j] = handle;
	dist[j] = d;
    }

    /**
     * Constructor.
     *
     * @param base the base node id.
     * @param size the size of the similar set.
     */

    public SimilarSet(NodeId base, int size, boolean cw) {
	baseId = base;
	clockwise = cw;
	theSize = 0;

	nodes = new NodeHandle[size];
	dist = new NodeId.Distance[size];
    }
    
    /**
     * Test is a NodeHandle belongs into the set. Predicts if a put would succeed.
     *
     * @param handle the handle to test.
     *
     * @return true if a put would succeed, false otherwise.
     */

    public boolean test(NodeHandle handle)
    {
	NodeId nid = handle.getNodeId();
	NodeId.Distance d;

	if (baseId.clockwise(nid) == clockwise)
	    d = baseId.distance(nid);
	else
	    d = baseId.longDistance(nid);
	
	int n = nodes.length;

	if (nid.equals(baseId)) return false;

	for (int i=0; i<theSize; i++)
	    if (nid.equals(nodes[i].getNodeId())) return false;

	if (theSize < n) return true;
	
	if (dist[n - 1].compareTo(d) <= 0) return false;

	return true;
    }


    /**
     * Puts a NodeHandle into the set.
     *
     * @param handle the handle to put.
     *
     * @return true if the put succeeded, false otherwise.
     */

    public boolean put(NodeHandle handle)
    {
	NodeId nid = handle.getNodeId();
	NodeId.Distance d;

	if (baseId.clockwise(nid) == clockwise)
	    d = baseId.distance(nid);
	else
	    d = baseId.longDistance(nid);
	
	int n = nodes.length;

	if (nid.equals(baseId)) return false;

	for (int i=0; i<theSize; i++)
	    if (nid.equals(nodes[i].getNodeId())) return false;
	
	int index;

	if (theSize < n) {
	    nodes[theSize] = handle;
	    dist[theSize] = d;
	    
	    index = theSize++;
	}
	else {
	    if (dist[n - 1].compareTo(d) <= 0) return false;

	    theSize --;
	    
	    setChanged();
	    notifyObservers(new NodeSetUpdate(nodes[n - 1], false));
	    
	    theSize++;

	    nodes[n - 1] = handle;
	    dist[n - 1] = d;
	    
	    index = n - 1;
	}

	for (int i=index; i>0; i--)
	    if (dist[i].compareTo(dist[i-1]) < 0) swap(i, i - 1);
	    else break;

	setChanged();
	notifyObservers(new NodeSetUpdate(handle, true));

	return true;
    }
    

    /**
     * Finds the NodeHandle associated with the NodeId.
     *
     * @param nid a node id.
     * @return the handle associated with that id or null if no such handle is found.
     */
    
    public NodeHandle get(NodeId nid)
    {
	for (int i=0; i<theSize; i++)
	    if (nodes[i].getNodeId().equals(nid)) return nodes[i];
	
	return null;
    }


    /**
     * Gets the ith element in the set.
     *
     * @param i an index.
     * @return the handle associated with that id or null if no such handle is found.
     */
    
    public NodeHandle get(int i) {
	if (i < 0 || i >= theSize) return null;
	
	return nodes[i]; 
    }
    
    /**
     * Verifies if the set contains this particular id.
     * 
     * @param nid a node id.
     * @return true if that node id is in the set, false otherwise.
     */

    public boolean member(NodeId nid) {
	for (int i=0; i<theSize; i++)
	    if (nodes[i].getNodeId().equals(nid)) return true;

	return false;
    }
    
    /**
     * Removes a node id and its handle from the set.
     *
     * @param nid the node to remove.
     *
     * @return the node handle removed or null if nothing.
     */

    public NodeHandle remove(NodeId nid) {
	for (int i=0; i<theSize; i++) {
	    if (nodes[i].getNodeId().equals(nid)) {
		NodeHandle handle = nodes[i];
		
		for (int j=i+1; j<theSize; j++)
		    nodes[j - 1] = nodes[j];
		
		theSize --;

		setChanged();
		notifyObservers(new NodeSetUpdate(handle, true));
		
		return handle;
	    }
	}

	return null;
    }

    /**
     * Gets the index of the element with the given node id.
     *
     * @param nid the node id.
     *
     * @return the index or -1 if the element does not exist.
     */

    public int getIndex(NodeId nid) throws NoSuchElementException {
	for (int i=0; i<theSize; i++) 
	    if (nodes[i].getNodeId().equals(nid)) return i;
	
	throw new NoSuchElementException();
    }
    
    /**
     * Gets the theSize of half the leaf set.
     *
     * @return the size.
     */

    public int size() { return theSize; }

    /**
     * Most similar node to a given a node.  Returns -1 if the base id is the most similar
     * and returns an index otherwise.
     *
     * @param nid a node id.
     *
     * @return -1 if the base id or the index most similar node.
     */

    public int mostSimilar(NodeId nid) {
	NodeId.Distance baseDist = baseId.distance(nid);
	NodeId.Distance d;

	if (theSize == 0) return -1;
	
	d = nodes[0].getNodeId().distance(nid);
	
	if (baseDist.compareTo(d) <= 0) return -1;

	for (int i=1; i<theSize; i++) {
	    NodeId.Distance dprime = nodes[i].getNodeId().distance(nid);

	    if (d.compareTo(dprime) <= 0) return i - 1;
	    
	    d = dprime;
	}

	return theSize - 1;
    }
}



