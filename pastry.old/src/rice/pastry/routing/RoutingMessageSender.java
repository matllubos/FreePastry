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
 * An interface for a routing message sender.
 *
 * @author Tsuen Wan Ngan
 */

public interface RoutingMessageSender
{
	/**
	 * Send a join request to a node found in the network.
	 *
	 * @param nodeAddress the IP address of a node in the network
	 * @param cred application-specific credentials containing information needed to authenticate the local node
	 *
	 * @return true if the join is successful
	 */

	public boolean join(InetAddress nodeAddress, PCredentials cred);
	
	/**
	 * Send its node set to an IP address.
	 *
	 * @param toNode the IP address of the node sending the node set to
	 * @param ns its node set
	 */

	public void sendNodeSet(InetAddress toNode, NodeSet ns);

	/**
	 * Send its leaf set to an IP address.
	 *
	 * @param toNode the IP address of the node sending the node set to
	 * @param ls its leaf set
	 */

	public void sendLeafSet(InetAddress toNode, LeafSet ls);

	/**
	 * Send a vector of routing table entries to an IP address.
	 *
	 * @param toNode the IP address of the node sending the node set to
	 * @param rt an array of routing table entries to be sent
	 */

	public void sendRoutingTable(InetAddress toNode, Vector rt);

	/**
	 * Send all state tables to an IP address.
	 *
	 * @param toNode the IP address of the node sending the node set to
	 * @param rt an array of routing table entries to be sent
	 * @param ns its node set
	 * @param ls its leaf set
	 */

	public void sendStateTables(InetAddress toNode, Vector rt, NodeSet ns, LeafSet ls);

	/**
	 * Request leaf set from another node.
	 *
	 * @param fromNode the IP address of the node to be requested
	 */

	public void requestLeafSetFrom(InetAddress fromNode);

	/**
	 * Request node set from another node.
	 *
	 * @param fromNode the IP address of the node to be requested
	 */

	public void requestNodeSetFrom(InetAddress fromNode);

	/**
	 * Request the whole routing table from another node.
	 *
	 * @param fromNode the IP address of the node to be requested
	 */

	public void requestRoutingTableFrom(InetAddress fromNode);

	/**
	 * Request specified rows of the routing table from another node.
	 *
	 * @param fromNode the IP address of the node to be requested
	 * @param fromRow the first row of the routing table requested
	 * @param toRow the last row of the routing table requested
	 */

	public void requestRoutingTableFrom(InetAddress fromNode, int fromRow, int toRow);

	/**
	 * Request all the status tables from another node.
	 *
	 * @param fromNode the IP address of the node to be requested
	 */

	public void requestStateTablesFrom(InetAddress fromNode);

	/**
	 * Sends a test message to a node to see if it is still alive.
	 *
	 * @return true if the node is alive
	 *
	 * @param testNode the IP address of the node being tested
	 */
	 
	public boolean isAlive(InetAddress testNode);
	
	/**
	 * Sends a node down message to a node.
	 * ?? Would this be an attack point for a malicious node?
	 *
	 * @param toNode the node the message to be sent to
	 * @param downNode the node id of the down node
	 */

	public void sendNodeDown(InetAddress toNode, NodeId downNode);

	/**
	 * Use IP multicast to locate the IP address of some close nodes.
	 *
	 * @return an array of IP addresses of replied nodes
	 *
	 * @param size the maximum number of replied nodes to be returned
	 */
	
	public Vector MulticastFind(int size);

	/**
	 * Get the distance from this node to another node.
	 * 
	 * @return the estimated distance between the nodes
	 *
	 * @param another the IP address of another node
	 */

	public int getDistance(InetAddress another);

	/**
	 * Test if the state tables of a node this node received is
	 * the latest.
	 *
	 * @return true if the last update is the same as last
	 *
	 * @param testNode the IP address of the tested node
	 * @param last the timestamp to be compared with
	 */

	public boolean statusIsLatest(InetAddress testNode, long last);
}
