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

import java.util.*;

import rice.pastry.*;
import rice.pastry.messaging.*;

/**
 * the node handle used with the direct network
 *
 * @version $Id$
 *
 * @author Andrew Ladd
 * @author Rongmei Zhang/Y. Charlie Hu
 */

public class DirectNodeHandle extends NodeHandle
{
    private PastryNode remoteNode;
    private NetworkSimulator simulator;

    public DirectNodeHandle(PastryNode ln, PastryNode rn, NetworkSimulator sim) {
      setLocalNode(ln);
      remoteNode = rn;
      simulator = sim;
    }

    public void notifyObservers(Object arg) {
      setChanged();
      super.notifyObservers(arg);
    }

    public PastryNode getRemote() { return remoteNode; }

    public NodeId getNodeId() { return remoteNode.getNodeId(); }

    public boolean isAlive() { return simulator.isAlive(remoteNode.getNodeId()); }

    public boolean ping() { return isAlive(); }

    public int proximity() {
    	assertLocalNode();
    	int result = simulator.proximity(getLocalNode().getNodeId(), remoteNode.getNodeId());
        
      return result;
    }

    public void receiveMessage(Message msg) {
      if (!isAlive()) {
  	    System.out.println("DirectNodeHandle: attempt to send message " + msg + " to a dead node!");
      }
        
    	simulator.deliverMessage(msg, remoteNode);
    }

    public NetworkSimulator getSimulator() {
    	return simulator;
    }

    /**
     * Equivalence relation for nodehandles. They are equal if and only
     * if their corresponding NodeIds are equal.
     *
     * @param obj the other nodehandle .
     * @return true if they are equal, false otherwise.
     */
    public boolean equals(Object obj) {
	    if (obj == null) return false;
	    NodeHandle nh = (NodeHandle)obj;

	    if(this.getNodeId().equals(nh.getNodeId()))
	      return true;
	    else
	      return false;
    }

    /**
     * Hash codes for node handles.It is the hashcode of
     * their corresponding NodeId's.
     *
     * @return a hash code.
     */
    public int hashCode(){
	    return this.getNodeId().hashCode();
    }
}
