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

package rice.pastry.*;
package rice.pastry.messaging.*;

import java.io.*;

/**
 * A route message is a pastry message which has been wrapped to
 * be sent to another pastry node.
 *
 * @author Andrew Ladd
 */

public class RouteMessage extends Message implements Serializable {
    private NodeId target;
    private Message internalMsg;

    /**
     * Constructor.
     *
     * @param target this is id of the node the message will be routed to.
     * @param msg the wrapped message.
     */

    public RouteMessage(NodeId target, Message msg) 
    {
	super(new RouterAddress());
	this.target = target;
	internalMsg = msg;
    }

    /**
     * Gets the target node id of this message.
     *
     * @return the target node id.
     */
    
    public NodeId getTarget() { return target; }

    /**
     * The wrapped message.
     *
     * @return the wrapped message.
     */

    public Message unwrap() { return internalMsg; }
    
    private void readObject(ObjectInputStream in)
	throws IOException, ClassNotFoundException 
    {
	target = (NodeId) in.readObject();
	internalMsg = (Message) in.readObject();
    }

    private void writeObject(ObjectOutputStream out)
	throws IOException, ClassNotFoundException 
    {
	out.writeObject(target);
	out.writeObject(internalMsg);
    }
}
