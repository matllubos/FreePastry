/*************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate 

Copyright 2002, Rice University. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither  the name  of Rice  University (RICE) nor  the names  of its
contributors may be  used to endorse or promote  products derived from
this software without specific prior written permission.

This software is provided by RICE and the contributors on an "as is"
basis, without any representations or warranties of any kind, express
or implied including, but not limited to, representations or
warranties of non-infringement, merchantability or fitness for a
particular purpose. In no event shall RICE or contributors be liable
for any direct, indirect, incidental, special, exemplary, or
consequential damages (including, but not limited to, procurement of
substitute goods or services; loss of use, data, or profits; or
business interruption) however caused and on any theory of liability,
whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even
if advised of the possibility of such damage.

********************************************************************************/

package rice.pastry.direct;

import rice.pastry.*;
import rice.pastry.messaging.*;
import rice.pastry.security.*;
import rice.pastry.standard.*;

import java.util.*;

/**
 * Security manager for direct connections between nodes.
 *
 * @version $Id$
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
	    return pnode.getLocalHandle();
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
