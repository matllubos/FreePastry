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

/**
 * This is a generic routing message request.
 * 
 * @author Andrew Ladd
 */

public class Request extends Message implements Serializable
{
    private NodeId reqId;
    private Address retAddr;
    
    /**
     * Constructor.
     *
     * @param dest the destination of this message.
     * @param reqId the node id of the node that is requesting.
     * @param retAddr the return address at the requester.
     * @param cred the credentials of the requester.
     */
    
    public Request(Address dest, NodeId reqId, Address retAddr, Credentials cred) 
    {
	super(dest, cred);
	
	this.reqId = reqId;
	this.retAddr = retAddr;
    }
    
    /**
     * Return the node id of the requester.
     *
     * @return the node id of the requester.
     */
    
    public NodeId getRequesterId()
    {
	return reqId;
    }
    
    /**
     * Return the return address for this message.
     *
     * @return the return address.
     */
    
    public Address getReturnAddress() 
    {
	return retAddr;
    }
    
    private void readObject(ObjectInputStream in)
	throws IOException, ClassNotFoundException 
    {
	reqId = (NodeId) in.readObject();
	retAddr = (Address) in.readObject();
    }
    
    private void writeObject(ObjectOutputStream out)
	throws IOException, ClassNotFoundException 
    {
	out.writeObject(reqId);
	out.writeObject(retAddr);
    }
}
