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
 * Creating a NodeSet from a Vector.
 *
 * @author Andrew Ladd
 */

public class NodeSetOfVector implements NodeSet {
    private Vector theSet;
    private boolean readOnly;
    
    public NodeSetOfVector(Vector handles) 
    {
	theSet = handles;
	readOnly = false;
    }

    public NodeSetOfVector(Vector handles, boolean isReadOnly) 
    {
	theSet = handles;
	readOnly = isReadOnly;
    }

    public boolean isReadOnly() { return readOnly; }

    public void putNode(NodeHandle handle) 
    {
	theSet.addElement(handle);
    }

    public NodeHandle findNode(NodeId nid)
    {
	Enumeration e = theSet.elements();

	while (e.hasMoreElements()) {
	    NodeHandle handle = (NodeHandle) e.nextElement();

	    NodeId handleNid = handle.getNodeId();
	    
	    if (handle.equals(nid)) return handle;
	}
	
	return null;
    }

    public NodeHandle removeNode(NodeId nid)
    {
	NodeHandle handle = findNode(nid);

	if (handle == null) return null;
	
	theSet.remove(handle);

	return handle;
    }

    public NodeHandle findClosestNode()
    {
	return null;
    }

    public NodeHandle findMostSimilarNode(NodeId nid) 
    {
	return null;
    }
}
