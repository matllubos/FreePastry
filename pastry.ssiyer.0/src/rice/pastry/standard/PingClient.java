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

package rice.pastry.standard;

import rice.pastry.*;
import rice.pastry.client.*;
import rice.pastry.routing.*;
import rice.pastry.messaging.*;
import rice.pastry.security.*;

import java.util.*;

/**
 * A very simple ping object.
 *
 * @author Andrew Ladd
 */

public class PingClient extends PastryClient {
    private static class PingAddress implements Address {
	private int myCode = 0x9219d8ff;
	
	public int hashCode() { return myCode; }

	public boolean equals(Object obj) {
	    return (obj instanceof PingAddress);
	}
    }
        
    private static Address pingAddress = new PingAddress();
    private Credentials pingCred = new PermissiveCredentials();

    private class PingMessage extends Message {
	private NodeId source;
	private NodeId target;

	public PingMessage(NodeId src, NodeId tgt) {
	    super(pingAddress);

	    source = src;
	    target = tgt;
	}
	
	public String toString() {
	    String s="";
	    
	    s += "ping from " + source + " to " + target;
	    return s;
	}
    }
    
    public PingClient(PastryNode pn) {
	super(pn);
    }

    public Address getAddress() { return pingAddress; }

    public Credentials getCredentials() { return pingCred; }

    public void sendPing(NodeId nid) {
	routeMessage(nid, new PingMessage(getNodeId(), nid), pingCred);
    }

    public void sendTrace(NodeId nid) {
	System.out.println("sending a trace from " + getNodeId() + " to " + nid);
	sendEnrouteMessage(nid, new PingMessage(getNodeId(), nid), pingCred);
    }

    public void messageForClient(Message msg) {
	System.out.print(msg);
	System.out.println(" received");
    }
    
    public boolean enrouteMessage(Message msg, NodeId from, NodeId nextHop, SendOptions opt) {
	System.out.print(msg);
	System.out.println(" at " + getNodeId());

	return true;
    }

    /*
    public void leafSetChange(NodeId nid, boolean wasAdded) {
	System.out.println("at... " + getNodeId() + "'s leaf set");
	System.out.print("node " + nid + " was ");
	if (wasAdded) System.out.println("added");
	else System.out.println("removed");
    }


    public void routeSetChange(NodeId nid, boolean wasAdded) {
	System.out.println("at... " + getNodeId() + "'s route set");
	System.out.print("node " + nid + " was ");
	if (wasAdded) System.out.println("added");
	else System.out.println("removed");
	}*/

}
