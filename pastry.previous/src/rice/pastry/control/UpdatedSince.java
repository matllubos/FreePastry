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

package rice.pastry.control;

import java.io.*;
import rice.pastry.*;
import rice.pastry.messaging.*;
import java.util.*;

/**
 * This is the routing message for querying whether the node 
 * has updated since a particular timestamp.
 * 
 * @author Tsuen Wan Ngan
 * @author Andrew Ladd
 */

public class UpdatedSince extends Message implements Serializable
{
    private Date timestamp;
    private NodeId source;
    
    /**
     * Constructor.
     *
     * @param dest the destination node.
     * @param src the source node.
     * @param timestamp the timestamp of the leaf set.
     */
    
    public UpdatedSince(Address dest, Credentials cred, NodeId src, Date timestamp) 
    {
	super(dest, cred);
	source = src;
	this.timestamp = timestamp;
    }
    
    /**
     * Get the time of the message.
     *
     * @return the time of the message.
     */
    
    public Date getTimestamp()
    {
	return timestamp;
    }
    
    /**
     * Get the node id of the source.
     *
     * @return the node id of the source.
     */
    
    public NodeId getSource()
    {
	return source;
    }
    
    private void readObject(ObjectInputStream in)
	throws IOException, ClassNotFoundException 
    {
	timestamp = (Date) in.readObject();
	source = (NodeId) source;
    }
    
    private void writeObject(ObjectOutputStream out)
	throws IOException, ClassNotFoundException 
    {
	out.writeObject(timestamp);
	out.writeObject(source);
    }
}


    
    

