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

import java.util.*;
import java.lang.*;

/**
 * Euclidean network topology and idealized node life.
 *
 * @version $Id$
 *
 * @author Andrew Ladd
 */

public class EuclideanNetwork implements NetworkSimulator 
{
    private Random rng;
    private HashMap nodeMap;
    private Vector msgQueue;

    private class MessageDelivery {
	private Message msg;
	private PastryNode node;

	public MessageDelivery(Message m, PastryNode pn) {
	    msg = m;
	    node = pn;
	}

	public void deliver() { 
	    //System.out.println("delivering to " + node);
	    //System.out.println(msg);
		    
	    node.receiveMessage(msg); 
	    //System.out.println("----------------------");
	}
    }

    private class NodeRecord {
	public int x, y;
	public boolean alive;

	public NodeRecord() {
	    x = rng.nextInt() % 10000;
	    y = rng.nextInt() % 10000;
	    
	    alive = true;
	}

	public int proximity(NodeRecord nr) {
	    int dx = x - nr.x;
	    int dy = y - nr.y;

	    if (dx < 0) dx = -dx;
	    if (dy < 0) dy = -dy;

	    return dx + dy;
	}
    }

    public EuclideanNetwork() {
	rng = new Random();
	nodeMap = new HashMap();
	msgQueue = new Vector();
    }
    
    public void registerNodeId(NodeId nid)
    {
	if (nodeMap.get(nid) != null) throw new Error("collision creating network stats for " + nid);
	nodeMap.put(nid, new NodeRecord());
    }

    public boolean isAlive(NodeId nid) {
	NodeRecord nr = (NodeRecord) nodeMap.get(nid);
	
	if (nr == null) throw new Error("asking about node alive for unknown node");

	return nr.alive;
    }

    public void setAlive(NodeId nid, boolean alive) {
	NodeRecord nr = (NodeRecord) nodeMap.get(nid);
	
	if (nr == null) throw new Error("setting node alive for unknown node");

	nr.alive = alive;
    }

    public int proximity(NodeId a, NodeId b)
    {
	NodeRecord nra = (NodeRecord) nodeMap.get(a);
	NodeRecord nrb = (NodeRecord) nodeMap.get(b);

	if (nra == null ||
	    nrb == null) throw new Error("asking about node proximity for unknown node(s)");

	return nra.proximity(nrb);
    }
    
    public void deliverMessage(Message msg, PastryNode node) {
	MessageDelivery md = new MessageDelivery(msg, node);

	msgQueue.addElement(md);
    }

    public boolean simulate() {
	if (msgQueue.size() == 0) return false;
	
	MessageDelivery md = (MessageDelivery) msgQueue.firstElement();

	msgQueue.removeElementAt(0);

	md.deliver();
	
	return true;
    }
}
