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

package rice.pastry.testing;

import rice.pastry.*;
import rice.pastry.routing.*;
import java.util.*;

/**
 * RoutingTableUnit tests the RoutingTable class.
 *
 * @author Andrew Ladd
 */

public class RoutingTableUnit {
    private Random rng;
    private RoutingTable rt;

    public NodeId createNodeId()
    {
	byte raw[] = new byte[NodeId.nodeIdBitLength >> 3];
	
	rng.nextBytes(raw);

	NodeId nodeId = new NodeId(raw);

	return nodeId;
    }
    
    public void createRoutingTable() 
    {
	NodeId nid = createNodeId();
	
	System.out.println("my node id: " + nid);

	rt = new RoutingTable(nid, 10);	
    }

    public NodeHandle createNodeHandle()
    {
	return new DummyNodeHandle(createNodeId());
    }
    
    public void fillRoutingTable(int num)
    {
	for (int i=0; i<num; i++) {
	    rt.put(createNodeHandle());
	}
    }

    public RoutingTableUnit() 
    {
	rng = new Random();
	createRoutingTable();

	fillRoutingTable(100000);
	
	System.out.println(rt);
    }

    public static void main(String args[]) 
    {
	RoutingTableUnit rtu = new RoutingTableUnit();
    }
}
