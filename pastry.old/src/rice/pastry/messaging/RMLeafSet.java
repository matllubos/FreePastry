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
 * This is the routing message with a leaf set object.
 * 
 * @author Tsuen Wan Ngan
 */

public class RMLeafSet implements Message
{
	private Address src;
	private LeafSet ls;
	private Long timestamp;

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
	 * @param dest the destination node.
	 * @param src the source node.
	 * @param ls the leaf set.
	 * @param timestamp the timestamp of the leaf set.
	 */

	public RMLeafSet(Address dest, Address src, LeafSet ls, long timestamp) 
	{
		super(dest);
		this.src = src;
		this.ls = ls;
		this.timestamp = new Long(timestamp);
	}

	/**
	 * Return the time of the message.
	 *
	 * @return the time of the message.
	 */

	public long getTime()
	{
		return timestamp.longvalue();
	}

	/**
	 * Return the leaf set.
	 *
	 * @return the leaf set.
	 */

	public LeafSet getLeafSet()
	{
		return ls;
	}

	/**
	 * Return the source node id.
	 *
	 * @return the node id of the source.
	 */

	public NodeId getSource()
	{
		return src;
	}

	private void readObject(ObjectInputStream in)
	throws IOException, ClassNotFoundException 
	{
		super(in);
		src = (NodeId) in.readObject();
		ls = (LeafSet) in.readObject();
		timestamp = (Long) in.readObject();
	}

	private void writeObject(ObjectOutputStream out)
	throws IOException, ClassNotFoundException 
	{
		super(out);
		out.writeObject(src);
		out.writeObject(ls);
		out.writeObject(timestamp);
	}
}
