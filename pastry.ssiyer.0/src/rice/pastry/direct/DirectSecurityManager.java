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

package rice.pastry.direct;

import rice.pastry.*;
import rice.pastry.messaging.*;
import rice.pastry.security.*;
import rice.pastry.standard.*;

import java.util.*;

/**
 * Security manager for direct connections between nodes.
 *
 * @author Andrew Ladd
 */

public class DirectSecurityManager implements PastrySecurityManager 
{
    private PastryNode pnode;
    private NetworkSimulator sim;

    /**
     * Constructor.
     */

    public DirectSecurityManager(NetworkSimulator ns) { 
	pnode = null;
	sim = ns;
    }

    /**
     * Sets the local pastry node.
     *
     * @param pn pastry node.
     */

    public void setLocalPastryNode(PastryNode local) { pnode = local; }

    /**
     * This method takes a message and returns true
     * if the message is safe and false otherwise.
     *
     * @param msg a message.
     * @return if the message is safe, false otherwise.
     */
    
    public boolean verifyMessage(Message msg) { return true; }

    /**
     * Checks to see if these credentials can be associated with the address.
     *
     * @param cred some credentials.
     * @param addr an address.
     *
     * @return true if the credentials match the address, false otherwise.
     */
    
    public boolean verifyAddressBinding(Credentials cred, Address addr) { return true; }

    /**
     * Verify node handle safety.
     *
     * @param handle the handle to check.
     *
     * @return the verified node handle
     */

    public NodeHandle verifyNodeHandle(NodeHandle handle) {
	NodeId local = pnode.getNodeId();
	NodeId nid = handle.getNodeId();

	if (local.equals(nid)) {
	    ProxyNodeHandle lnh = new ProxyNodeHandle(local);
	    
	    lnh.setProxy(pnode);
	    
	    return lnh;
	}
	else if (handle instanceof PastryNode) {
	    DirectNodeHandle dnh = new DirectNodeHandle(pnode, (PastryNode) handle, sim);

	    return dnh;
	}
	else if (handle instanceof ProxyNodeHandle) {
	    ProxyNodeHandle lnh = (ProxyNodeHandle) handle;
	    
	    DirectNodeHandle dnh = new DirectNodeHandle(pnode, lnh.getNode(), sim);

	    return dnh;
	}
	else if (handle instanceof DirectNodeHandle) {
	    DirectNodeHandle dnh = (DirectNodeHandle) handle;

	    DirectNodeHandle retDnh = new DirectNodeHandle(pnode, dnh.getRemote(), sim);

	    return retDnh;
	}
	else throw new Error("node handle of unknown type");	
    }

    /**
     * Gets the current time for a timestamp.
     *
     * @return the timestamp.
     */
    
    public Date getTimestamp() { return new Date(); }
}
