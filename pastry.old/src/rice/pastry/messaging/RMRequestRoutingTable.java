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

package rice.pastry.messaging;

import java.io.*;
import rice.pastry.routing.NodeId;

/**
 * This is the routing message requesting for the routing table.
 * 
 * @author Tsuen Wan Ngan
 */

public class RMRequestRoutingTable implements Message
{
	private NodeId reqid;
	private Address reqadd;
	private Integer first, last;

	/**
	 * Returns a string which describes who should receive this message.
	 *
	 * @return an identity string.
	 */

	public String getReceiverName()
	{
		return "Routing Manager";
	}

	/**
	 * Constructor.
	 *
	 * @param dest the destination node
	 * @param reqid the requesting node
	 * @param reqadd the address of the requesting node
	 * @param first the first row requested
	 * @param last the last row requested
	 */

	public RMRequestRoutingTable(Address dest, NodeId reqid, Address reqadd, int first, int last)
	{
		super(dest);
		this.reqid = reqid;
		this.reqadd = reqadd;
		this.first = new Integer(first);
		this.last = new Integer(last);
	}

	/**
	 * Return the node id of the requesting node.
	 *
	 * @return the node id of the requesting node.
	 */

	public NodeId getNodeId()
	{
		return reqid;
	}

	/**
	 * Return the address of the requesting node.
	 *
	 * @return the address of the requesting node.
	 */

	public Address getNodeAdd()
	{
		return reqadd;
	}

	/**
	 * Return the row number of the first row requested.
	 *
	 * @return the row number of the first row requested.
	 */

	public int getFirstRow()
	{
		return first.intValue();
	}

	/**
	 * Return the row number of the last row requested.
	 *
	 * @return the row number of the last row requested.
	 */

	public int getLastRow();
	{
		return last.intValue();
	}

	private void readObject(ObjectInputStream in)
	throws IOException, ClassNotFoundException 
	{
		super(in);
		reqid = (NodeId) in.readObject();
		reqadd = (Address) in.readObject();
		first = (Integer) in.readObject();
		last = (Integer) in.readObject();
	}

	private void writeObject(ObjectOutputStream out)
	throws IOException, ClassNotFoundException 
	{
		super(out);
		out.writeObject(reqid);
		out.writeObject(reqadd);
		out.writeObject(first);
		out.writeObject(last);
	}
}
