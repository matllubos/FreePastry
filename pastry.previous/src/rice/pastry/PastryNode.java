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

package rice.pastry;

import rice.pastry.messaging.*;

/**
 * A Pastry node is single entity in the pastry network.
 *
 * @author Andrew Ladd
 */

public class PastryNode implements NodeHandle, MessageReceiver
{
    private NodeId myNodeId;
    private RoutingManager myRoutingManager;
    private SecurityManager mySecurityManager;
    private MessageDispatch myMessageDispatch;

    /**
     * Constructor.
     *
     * @param pFactory a Pastry node factory.
     */

    public PastryNode(PastryNodeFactory pFactory) 
    {
	pFactory.constructNode();
	
	myNodeId = pFactory.getNodeId();
	myRoutingManager = pFactory.getRoutingManager();
	mySecurityManager = pFactory.getSecurityManager();
	myMessageDispatch = pFactory.getMessageDispatch();

	myMessageDispatch.registerReceiver(myRoutingManager.getAddress(), myRoutingManager);
	
	pFactory.doneWithNode();
    }

    // node handle interface

    public NodeId getNodeId() { return myNodeId; }
    
    public boolean isAlive() { return true; }

    public int proximity() { return 0; }
    
    // message receiver interface

    public void receiveMessage(Message msg) 
    {
	if (mySecurityManager.verifyMessage(msg) == true)
	    myMessageDispatch.dispatchMessage(msg);
    }

    /** 
     * Registers a message receiver with this Pastry node.
     *
     * @param address the address that the receiver will be at.
     * @param receiver the message receiver.
     */

    public void registerReceiver(Credentials cred, Address address, MessageReceiver receiver) 
    {
	if (mySecurityManager.verifyAddressBinding(cred, address) == true)
	    myMessageDispatch.registerReceiver(address, receiver);	
    }    
}
