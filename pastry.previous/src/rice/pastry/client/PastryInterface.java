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

package rice.pastry.client;

import rice.pastry.*;
import rice.pastry.control.*;
import rice.pastry.messaging.*;
import rice.pastry.routing.*;

/**
 * This object wraps a PastryNode for a given client to provide syntactic sugar for
 * the operations that the client needs to do.
 *
 * @author Andrew Ladd
 */

public class PastryInterface 
{
    private PastryClient theClient;
    private PastryNode theNode;

    public PastryInterface(PastryClient client, PastryNode node) 
    {
	theClient = client;
	theNode = node;

	registerAddress(theClient.getDefaultCredentials(), theClient.getDefaultAddress());
    }

    /**
     * Registers an address to the client.
     *
     * @param cred the credentials of the client.
     * @param addr the address to register.
     */

    public void registerAddress(Credentials cred, Address addr) 
    {
	theNode.registerReceiver(cred, addr, theClient);
    }

    /**
     * Send a message to a remote node by node id.
     *
     * @param nid the node id to send to.
     * @param msg the message to send.
     * @param opts the send options for the message.
     * @param cred the credentials for the message.
     */

    public void sendRemoteMessage(NodeId nid, Message msg, Credentials cred, SendOptions opts)
    {
	Message rm = new RouteMessage(nid, msg, cred, opts);

	theNode.receiveMessage(rm);
    }

    /**
     * Request node handle for a node id.
     *
     * @param nid a node id.
     * @param cred credentials for this request.
     * @param bestMatch this determines if best match is acceptable (true) or if an exact match is required (false)
     * @param remote true if it is acceptable 
     *
     * @return a handle for that id or null if no handle can be found.
     */

    public void requestNodeHandle(NodeId nid, Credentials cred, boolean bestMatch, boolean remote) 
    {
	Address retAddr = theClient.getDefaultAddress();
	
	Request req = new RequestHandle(theNode.getNodeId(), retAddr, cred);

	sendRemoteMessage(nid, req, cred, new SendOptions());
    }

    /**
     * Called by a client to obtain a copy (view) of the leaf set.
     *
     * @return a view of the leaf set.
     */
    
    public LeafSet getLeafSet() { return null; }
    
    /**
     * Called by a client to obtain a copy (view) of the neighbourhood 
     * set.
     *
     * @return a view of the neighborhood set.
     */
    
    public NeighbourSet getNeighbourSet() { return null; }
    
    /**
     * Called by a client to obtain a copy (view) of the routing table.
     * The routing table may be incomplete or contain failed nodes.
     *
     * @return a view of the routing table.
     */
    
    public RoutingTable getRoutingTable() { return null; }
}

