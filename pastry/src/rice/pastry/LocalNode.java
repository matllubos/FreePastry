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

package rice.pastry;

import java.io.*;
import java.util.*;

import rice.pastry.messaging.*;
import rice.pastry.rmi.*;

/**
 * Abstract class that some Serializable classes (such as NodeHandle and
 * Certificate) extend, if they want to be kept informed of what node
 * they're on. Think of this as a pattern.
 *
 * @version $Id$
 *
 * @author Sitaram Iyer
 */

public abstract class LocalNode implements Serializable {

    private transient PastryNode localnode;

    public LocalNode() { localnode = null; }

    /**
     * Accessor method.
     */
    public final PastryNode getLocalNode() { return localnode; }

    /**
     * Accessor method. Notifies the overridable afterSetLocalNode.
     */
    public final void setLocalNode(PastryNode pn) {
	localnode = pn;
	if (localnode != null) afterSetLocalNode();
    }

    /**
     * Method that can be overridden by handle to set isLocal, etc.
     */
    public void afterSetLocalNode() {}

    /**
     * May be called from handle etc methods to ensure that local node has
     * been set, either on construction or on deserialization/receivemsg.
     */
    public final void assertLocalNode() {
	if (localnode == null) {
	    System.out.println("PANIC: localnode is null in " + this);
	    (new Exception()).printStackTrace();
	}
    }

    /**
     * Refer to README.handles_localnode.
     */
    private static HashMap pending = new HashMap();

    /**
     * Called on deserialization. Adds itself to a pending-setLocalNode
     * list. This list is in a static (global) hash, indexed by the
     * ObjectInputStream. Refer to README.handles_localnode for details.
     */
    private void readObject(ObjectInputStream in)
	throws IOException, ClassNotFoundException 
    {
	in.defaultReadObject();
	localnode = null;

	LinkedList pendinglist;

	if (LocalNode.pending.containsKey(in) == false)
	    LocalNode.pending.put(in, pendinglist = new LinkedList());
	else
	    pendinglist = (LinkedList) LocalNode.pending.get(in);

	if (Log.ifp(6))
	    System.out.println("pending " + this + " on list " + in);
	pendinglist.add(this);
    }

    /**
     * Node.receiveMessage calls this to set the node reference on pending
     * node handles and other LocalNodes. Refer to README.handles_localnode.
     */
    public static void setPendingLocalNodes(Object key, PastryNode pn)
    {
	LinkedList pending = (LinkedList) LocalNode.pending.remove(key);
	if (pending != null) {
	    Iterator iter = pending.iterator();
	    while (iter.hasNext()) {
		LocalNode ln = (LocalNode) iter.next();
		if (Log.ifp(6))
		    System.out.println("setting local node " + pn + " to " + ln);
		if (ln.getLocalNode() != null)
		    System.out.println("setting local node twice! " + pn + " to " + ln);
		ln.setLocalNode(pn);
	    }
	}
    }
}
