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

package rice.pastry.direct;

import rice.pastry.*;
import rice.pastry.messaging.*;

/**
 * Interface to an object which is simulating the network.
 *
 * @author Andrew Ladd
 */

public interface NetworkSimulator 
{
    /**
     * Registers a node id with the simulator.
     *
     * @param nid the node id to register.
     */

    public void registerNodeId(NodeId nid);

    /**
     * Checks to see if a node id is alive.
     *
     * @param nid a node id.
     *
     * @return true if alive, false otherwise.
     */

    public boolean isAlive(NodeId nid);

    /**
     * Determines proximity between two nodes.
     *
     * @param a a node id.
     * @param b another node id.
     *
     * @return proximity of b to a.
     */

    public int proximity(NodeId a, NodeId b);

    /**
     * Deliver message.
     *
     * @param msg message to deliver.
     * @param node the Pastry node to deliver it to.
     */

    public void deliverMessage(Message msg, PastryNode node);

    /**
     * Simulate one message delivery.
     *
     * @return true if a message was delivered, false otherwise.
     */

    public boolean simulate();
}
