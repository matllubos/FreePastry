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
import rice.pastry.security.*;
import rice.pastry.client.*;
import rice.pastry.leafset.*;
import rice.pastry.routing.*;

import java.util.*;

/**
 * A Pastry node is single entity in the pastry network.
 *
 * @author Andrew Ladd
 */

public class PastryNode implements NodeHandle 
{
    private NodeId myNodeId;
    private PastrySecurityManager mySecurityManager;
    private MessageDispatch myMessageDispatch;
    private LeafSet leafSet;
    private RoutingTable routeSet;
  
    /**
     * Constructor.  Creates a new Pastry network.
     *
     * @param pFactory a Pastry node factory.
     */

    public PastryNode(PastryNodeFactory pFactory) 
    {
	pFactory.constructNode();
	
	myNodeId = pFactory.getNodeId();
	mySecurityManager = pFactory.getSecurityManager();
	myMessageDispatch = pFactory.getMessageDispatch();
	
	leafSet = pFactory.getLeafSet();
	routeSet = pFactory.getRouteSet();

	pFactory.doneWithNode(this);
    }

    // node handle interface

    public final NodeId getNodeId() { return myNodeId; }
    
    public final boolean isAlive() { return true; }

    public final int proximity() { return 0; }

    public final LeafSet getLeafSet() { return leafSet; }

    public final RoutingTable getRoutingTable() { return routeSet; }

    /**
     * Add a leaf set observer to the Pastry node.
     *
     * @param o the observer.
     */
    
    public final void addLeafSetObserver(Observer o) { leafSet.addObserver(o); }
    
    /**
     * Add a route set observer to the Pastry node.
     *     
     * @param o the observer.
     */
    
    public final void addRouteSetObserver(Observer o) { routeSet.addObserver(o); }

    // message receiver interface

    public final void receiveMessage(Message msg) 
    {
	if (mySecurityManager.verifyMessage(msg) == true)
	    myMessageDispatch.dispatchMessage(msg); 
    }

    /** 
     * Registers a message receiver with this Pastry node.
     *
     * @param cred the credentials.
     * @param address the address that the receiver will be at.
     * @param receiver the message receiver.
     */

    public final void registerReceiver(Credentials cred, Address address, MessageReceiver receiver) 
    {
	if (mySecurityManager.verifyAddressBinding(cred, address) == true)
	    myMessageDispatch.registerReceiver(address, receiver);	
	else throw new Error("security failure");
    }    

    /**
     * Registers a client with the Pastry network.
     *
     * @param client the client to register.
     */

    public final void registerClient(PastryClient client) {
	if (client.getAddress() == null) throw new NullPointerException();
	
	registerReceiver(client.getCredentials(), client.getAddress(), client);
    }

    public String toString() {
	return "Pastry node " + myNodeId.toString();
    }
}
