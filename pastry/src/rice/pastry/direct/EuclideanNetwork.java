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

import java.util.*;
import java.lang.*;

/**
 * Euclidean network topology and idealized node life.
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
