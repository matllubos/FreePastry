
package rice.pastry;

import java.util.*;

/**
 * An interface to a generic set of nodes.
 *
 * @version $Id$
 *
 * @author Andrew Ladd
 */

public interface NodeSetI extends rice.p2p.commonapi.NodeHandleSet
{       
    /**
     * Puts a NodeHandle into the set.
     *
     * @param handle the handle to put.
     *
     * @return true if the put succeeded, false otherwise.
     */

    public boolean put(NodeHandle handle);
    
    /**
     * Finds the NodeHandle associated with the NodeId.
     *
     * @param nid a node id.
     * @return the handle associated with that id or null if no such handle is found.
     */
    
    public NodeHandle get(NodeId nid);


    /**
     * Gets the ith element in the set.
     *
     * @param i an index.
     * @return the handle associated with that id or null if no such handle is found.
     */
    
    public NodeHandle get(int i);
    
    /**
     * Verifies if the set contains this particular id.
     * 
     * @param nid a node id.
     * @return true if that node id is in the set, false otherwise.
     */

    public boolean member(NodeHandle nh);
    
    /**
     * Removes a node id and its handle from the set.
     *
     * @param nid the node to remove.
     *
     * @return the node handle removed or null if nothing.
     */

    public NodeHandle remove(NodeHandle nh);
        
    /**
     * Gets the size of the set.
     *
     * @return the size.
     */

    public int size();

    /**
     * Gets the index of the element with the given node id.
     *
     * @param nid the node id.
     *
     * @return the index or throws a NoSuchElementException.
     */

  public int getIndex(NodeId nid) throws NoSuchElementException;

  public int getIndex(NodeHandle nh) throws NoSuchElementException;
}
