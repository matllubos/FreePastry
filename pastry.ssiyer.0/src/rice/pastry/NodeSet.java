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

import java.util.*;

/**
 * An interface to a generic set of nodes.
 *
 * @author Andrew Ladd
 */

public interface NodeSet 
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

    public boolean member(NodeId nid);
    
    /**
     * Removes a node id and its handle from the set.
     *
     * @param nid the node to remove.
     *
     * @return the node handle removed or null if nothing.
     */

    public NodeHandle remove(NodeId nid);
        
    /**
     * Gets the size of half the leaf set.
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
}
