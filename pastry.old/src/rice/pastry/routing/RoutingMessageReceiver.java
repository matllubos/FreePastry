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

/**
 * An interface for any routing message receiver.
 *
 * @author Tsuen Wan Ngan
 */

public interface RoutingMessageReceiver
{
	/**
	 * This function is called when a new node requests to join 
	 * the network via the current node.
	 * 
	 * @param newId the node id of the new node
	 * @param newAddress the IP address of the new node
	 * @param pcred the application-specific credentials for authentication of the joining node
	 */
	 
	public void joinRequest(NodeId newId, InetAddress newAddress, PCredentials pcred);

	/**
	 * This function is called when a node X received a join request 
	 * from a new node and requests this node to forward the request 
	 * to the numerically closest node.
	 *
	 * @param newId the node id of the new node
	 * @param newAddress the IP address of the new node
	 */
	 
	public void sendToClosestId(NodeId newId, InetAddress newAddress);

	/**
	 * Received a leaf set.
	 *
	 * @param ls the received leaf set
	 * @param sendTime the time the leaf set is retrieved
	 */
	 
	public void receiveLeafSet(LeafSet ls, long sendTime);

	/**
	 * Received a node set.
	 *
	 * @param ns the received node set
	 * @param sendTime the time the leaf set is retrieved
	 */
	 
	public void receiveNodeSet(NodeSet ns, long sendTime);

	/**
	 * Received some routing table entries.
	 *
	 * @param rt a vector of received routing table entries
	 * @param sendTime the time the leaf set is retrieved
	 */
	 
	public void receiveRoutingTable(Vector rt, long sendTime);

	/**
	 * Received all state tables.
	 *
	 * @param rt a vector of received routing table entries
	 * @param ns the received node set
	 * @param ls the received leaf set
	 * @param sendTime the time the leaf set is retrieved
	 */
	 
	public void receiveStateTables(Vector rt, NodeSet ns, LeafSet ls, long sendTime);

	/**
	 * A request for the leaf set of this node from another node.
	 *
	 * @param fromNode the IP address of the node requesting
	 */

	public void requestLeafSet(InetAddress fromNode);
	
	/**
	 * A request for the node set of this node from another node.
	 *
	 * @param fromNode the IP address of the node requesting
	 */

	public void requestNodeSet(InetAddress fromNode);
	
	/**
	 * A request for the whole routing table of this node from another 
	 * node.
	 *
	 * @param fromNode the IP address of the node requesting
	 */

	public void requestRoutingTable(InetAddress fromNode);
	
	/**
	 * A request for specific rows of the routing table of this node 
	 * from another node.
	 *
	 * @param fromNode the IP address of the node requesting
	 * @param fromRow the first row of the routing table requested
	 * @param toRow the last row of the routing table requested
	 */

	public void requestRoutingTable(InetAddress fromNode, int fromRow, int toRow);
	
	/**
	 * A request for all state tables of this node from another node.
	 *
	 * @param fromNode the IP address of the node requesting
	 */

	public void requestStateTables(InetAddress fromNode);
	
	/**
	 * Received a message that a node has arrived.
	 *
	 * @param nodeId the node id of the newly arrived node
	 * @param nodeIP the IP address of the newly arrived node
	 */
	 
	public void nodeArrived(NodeId nodeId, InetAddress nodeIP);

	/**
	 * A node is recently down.
	 *
	 * @param downId the node id of the down node
	 */
	 
	public void nodeDown(NodeId downId);

	/**
	 * Check whether the state tables have been updated since a 
	 * particular time.
	 *
	 * @return true if the state tables have been updated since timestamp
	 *
	 * @param timestamp the timestamp to be compared with
	 */

	public boolean updatedSince(long timestamp);
}

