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
import rice.util.*;

import java.util.*;

/**
 * A class for representing and manipulating the leaf set.
 *  
 * @author Andrew Ladd
 */

public class LeafSet {
    private SimilarSet cwSet;
    private SimilarSet ccwSet;
    private NodeId base;
    
    public LeafSet(NodeId nid, int size) 
    {
	cwSet = new SimilarSet(nid, size);
	ccwSet = new SimilarSet(nid, size);
	base = nid;
    }

    /**
     * Puts a NodeHandle into the set.
     *
     * @param handle the handle to put.
     */

    public void put(NodeHandle handle) 
    {
	NodeId nid = handle.getNodeId();

	if (base.clockwise(nid) == true) cwSet.put(handle);
	else ccwSet.put(handle);
    }

    /**
     * Finds the NodeHandle associated with the NodeId.
     *
     * @param nid a node id.
     * @return the handle associated with that id or null if no such handle is found.
     */
    
    public NodeHandle get(NodeId nid) 
    {
	if (base.clockwise(nid) == true) return cwSet.get(nid);
	else return ccwSet.get(nid);
    }
    
    /**
     * Verifies if the set contains this particular id.
     * 
     * @param nid a node id.
     * @return true if that node id is in the set, false otherwise.
     */

    public boolean containsId(NodeId nid) 
    {
	if (base.clockwise(nid) == true) return cwSet.containsId(nid);
	else return ccwSet.containsId(nid);
    }
    
    /**
     * Removes a node id and its handle from the set.
     *
     * @param nid the node to remove.
     */

    public void remove(NodeId nid) 
    {
	if (base.clockwise(nid) == true) cwSet.remove(nid);
	else ccwSet.remove(nid);
    }
    
    
    /**
     * Get the clockwise node set.
     *
     * @return the clockwise node set.
     */

    public SimilarSet getClockwiseSet() { return cwSet; }
    
    /**
     * Get the counterclockwise node set.
     *
     * @return the counterclockwise node set.
     */
    
    public SimilarSet getCounterClockwiseSet() { return ccwSet; }
}






