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

public class NodeSet {
    private HashMap theMap;

    public NodeSet() 
    {
	theMap = new HashMap();
    }

    /**
     * Adds a node to the node set.
     *
     * @param handle the handle of the node to add.
     */
    
    public void putNode(NodeHandle handle)
    {
	NodeId nid = handle.getNodeId();

	theMap.put(nid, handle);
    }

    /**
     * Finds a node handle in the node set with a given node id.
     *
     * @param nid a node id.
     *
     * @return the handle of the node we found.
     */

    public NodeHandle findNode(NodeId nid) 
    {
	return (NodeHandle) theMap.get(nid);
    }

    /**
     * Removes a node from the node set.
     *
     * @param nid which node to remove.
     *
     * @return the handle of the node that was removed or null if nothing was removed.
     */

    public NodeHandle removeNode(NodeId nid) 
    {
	return (NodeHandle) theMap.remove(nid);
    }
}
