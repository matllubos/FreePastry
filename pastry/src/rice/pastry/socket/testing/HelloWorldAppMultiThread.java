/*************************************************************************

"Free Pastry" Peer-to-Peer Application Development Substrate 

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

package rice.pastry.socket.testing;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Random;

import rice.pastry.Id;
import rice.pastry.Log;
import rice.pastry.NodeHandle;
import rice.pastry.NodeId;
import rice.pastry.PastryNode;
import rice.pastry.client.PastryAppl;
import rice.pastry.leafset.LeafSet;
import rice.pastry.messaging.Address;
import rice.pastry.messaging.Message;
import rice.pastry.routing.RouteSet;
import rice.pastry.routing.RoutingTable;
import rice.pastry.routing.SendOptions;
import rice.pastry.security.Credentials;
import rice.pastry.security.PermissiveCredentials;
import rice.pastry.socket.SocketNodeHandle;
import rice.pastry.socket.SocketPastryNode;

/**
 * A hello world example for pastry. This is the per-node app object.
 *
 * @version $Id$
 *
 * @author Jeff Hoye, Sitaram Iyer
 */

public class HelloWorldAppMultiThread extends PastryAppl {

	private int msgid = 0;

	private static Address addr = new HelloAddress();
	private static Credentials cred = new PermissiveCredentials();

	private static class HelloAddress implements Address {
		private int myCode = 0x1984abcd;

		public int hashCode() {
			return myCode;
		}

		public boolean equals(Object obj) {
			return (obj instanceof HelloAddress);
		}

		public String toString() {
			return "[HelloAddress]";
		}
	}
	/*
	public HelloWorldApp(PastryNode pn) {
	super(pn);
	}
	*/
	/**
	 * Sends a message to a randomly chosen node. Yeah, for fun.
	 *
	 * @param rng Random number generator.
	 */
	public void sendRndMsg(Random rng) {
		Id rndid = Id.makeRandomId(rng);
		if (Log.ifp(5))
			System.out.println(
				"Sending message from " + getNodeId() + " to random dest " + rndid);
		Message msg =
			new HelloMsg(addr, (SocketNodeHandle)thePastryNode.getLocalHandle(), rndid, ++msgid);      
		routeMsg(rndid, msg, cred, new SendOptions());
	}

	/**
	 * Sends a message to a randomly chosen node. Yeah, for fun.
	 *
	 * @param rng Random number generator.
   * @param id Hello Message Id
	 */
  public void sendRndMsg(Random rng, int id) {    
    if (DistHelloWorldMultiThread.useDirect && DistHelloWorldMultiThread.useNonDirect) {
      if (rng.nextInt(2) == 0) {
        directRndMsg(rng,id);
      } else {
        routeRndMsg(rng,id);
      }
      return;     
    }
    if (DistHelloWorldMultiThread.useDirect) {
      directRndMsg(rng,id);
    } else {
      routeRndMsg(rng,id);
    }
  }
  
	public void routeRndMsg(Random rng, int id) {
		Id rndid = Id.makeRandomId(rng);
		if (Log.ifp(5)) {
			//	    System.out.println("Sending message # " + id + " from " + getNodeId() + " to random dest " + rndid);
		}
		HelloMsg msg =
			new HelloMsg(addr, (SocketNodeHandle)thePastryNode.getLocalHandle(), rndid, id);
    msg.messageDirect = false;
		routeMsg(rndid, msg, cred, new SendOptions());
		driver.MessageSent(msg);
	}
  
  public NodeHandle getHandleFromId(Id id) {
    HashSet nodes = getKnownHandles();
    Iterator i = nodes.iterator();    
    while(i.hasNext()) {
      NodeHandle choice = (NodeHandle)i.next();
      if (choice.getId().equals(id))
        return choice;
    }    
    return null;
  }

  public HashSet getKnownHandles() {
    HashSet nodes = new HashSet();    
    RoutingTable rt = thePastryNode.getRoutingTable();
    int numR = rt.numRows();
    int numC = rt.numColumns();
    for (int r = 0; r < numR; r++) {
      for (int c = 0; c < numC; c++) {
        RouteSet rs = rt.getRouteSet(r,c);
        if (rs != null)
          for (int a = 0; a < rs.size(); a++) 
            nodes.add(rs.get(a));
      }
    }
    
    LeafSet ls = thePastryNode.getLeafSet();
    int size = ls.ccwSize();    
    try {
      for (int i = 0; i < size; i++) {
        nodes.add(ls.get(-i));
      }
    } catch (ArrayIndexOutOfBoundsException aioobe) { // LS may change while processing it
    }
    
    size = ls.cwSize();    
    try {
      for (int i = 0; i < size; i++) {
        nodes.add(ls.get(i));
      }
    } catch (ArrayIndexOutOfBoundsException aioobe) { // LS may change while processing it
    }

    return nodes;
  }

  public NodeHandle getRandomNodeHandle(Random rng) {
    HashSet nodes = getKnownHandles();
    //System.out.println("HWAMT.getRandomNodeHandle() num nodes="+nodes.size());
    int index = rng.nextInt(nodes.size());
    Iterator i = nodes.iterator();    
    NodeHandle choice = (NodeHandle)i.next();
    int ctr = 1;
    while(ctr <= index) {
      choice = (NodeHandle)i.next();
      ctr++;
    }
    return choice;
  }
  
  public void directRndMsg(Random rng, int id) {
    NodeHandle nh = getRandomNodeHandle(rng);
    HelloMsg msg =
      new HelloMsg(addr, (SocketNodeHandle)thePastryNode.getLocalHandle(), nh.getId(), id);
    msg.messageDirect = true;

    msg.setNextHop(nh);
//    routeMsg(rndid, msg, cred, new SendOptions());
    if (routeMsgDirect(nh,msg,cred,new SendOptions())) {
      driver.MessageSent(msg);
    }
  }
  
	// The remaining methods override abstract methods in the PastryAppl API.

	/**
	 * Get address.
	 *
	 * @return the address of this application.
	 */
	public Address getAddress() {
		return addr;
	}

	/**
	 * Get credentials.
	 *
	 * @return credentials.
	 */
	public Credentials getCredentials() {
		return cred;
	}

	/**
	 * Invoked on destination node when a message arrives.
	 *
	 * @param msg Message being routed around
	 */
	public void messageForAppl(final Message msg) {
		/*     
		     Thread t = new Thread(){
		         public void run(){
		             try{
		                 Thread.currentThread().sleep(driver.rng.nextInt(2000));*/
		if (msg instanceof HelloMsg) {
			HelloMsg hmsg = (HelloMsg) msg;
			hmsg.setActualReceiver((SocketNodeHandle)thePastryNode.getLocalHandle()); //getNodeId();
			driver.MessageRecieved(hmsg);
			//if (Log.ifp(5))
			//  System.out.println("Received " + msg + " at " + getNodeId());
			//Thread.dumpStack();
		}
		/*else throw new Exception("Message not recognized");
		
		}catch(Exception e){
		System.out.println("Error Recieving an appl message");
		}
		}
		};
		t.start();*/
		return;
	}

	/**
	 * Invoked on intermediate nodes in routing path.
	 *
	 * @param msg Message that's passing through this node.
	 * @param key destination
	 * @param nextHop next hop
	 * @param opt send options
	 * @return true if message needs to be forwarded according to plan.
	 */
	public boolean enrouteMessage(
		Message msg,
		Id key,
		NodeId nextHop,
		SendOptions opt) {
		//if (Log.ifp(5))
		if (msg instanceof HelloMsg) {
      HelloMsg hmsg = (HelloMsg) msg;
      hmsg.setIntermediateSource((SocketNodeHandle)thePastryNode.getLocalHandle());
      hmsg.setLocalNode((SocketPastryNode)thePastryNode);

      hmsg.setNextHop(getHandleFromId(nextHop));
			driver.updateMsg(hmsg);
//      System.out.println("Enroute " + msg + " at " + getNodeId() +" from "+hmsg.source);
    }
		return true;
	}

	/**
	 * Invoked upon change to leafset.
	 *
	 * @param nh node handle that got added/removed
	 * @param wasAdded added (true) or removed (false)
	 */
	public void leafSetChange(NodeHandle nh, boolean wasAdded) {
		/*
		if(driver.allNodesCreated()){
		    if (Log.ifp(5)) {
		        System.out.print("In " + getNodeId() + "'s leaf set, " +
		            	     "node " + nh.getNodeId() + " was ");
		        if (wasAdded) System.out.println("added");
		        else System.out.println("removed");
		    }   
		}*/
	}

	/**
	 * Invoked upon change to routing table.
	 *
	 * @param nh node handle that got added/removed
	 * @param wasAdded added (true) or removed (false)
	 */
	public void routeSetChange(NodeHandle nh, boolean wasAdded) {
		/*
		if (Log.ifp(5)) {
		System.out.print("In " + getNodeId() + "'s route set, " +
		   "node " + nh.getNodeId() + " was ");
		if (wasAdded) System.out.println("added");
		else System.out.println("removed");
		}*/
	}

	/**
	 * Invoked by {RMI,Direct}PastryNode when the node has something in its
	 * leaf set, and has become ready to receive application messages.
	 */
	public void notifyReady() {

		//if (Log.ifp(6))
		System.out.println(
			"Node "
				+ thePastryNode.getLocalHandle()
				+ " ready, waking up any clients");
		//sendRndMsg(new Random());

	}

	//For the purpose of global bookKeeping, we are allowing
	//every PastryAppl access to the driver
	private DistHelloWorldMultiThread driver;

	public HelloWorldAppMultiThread(
		PastryNode pn,
		DistHelloWorldMultiThread drv) {
		super(pn);
		driver = drv;
		driver.node_created();
	}

  /**
   * Yee ol toString()
   */
	public String toString() {
		return new String("DistHWMTApp:" + thePastryNode);
	}

//	public void messageNotDelivered(Message msg, int errorCode) {
//    if (errorCode == SocketPastryNode.EC_CONNECTION_FAULTY) {
//      Thread.dumpStack();
//    } else {
//      super.messageNotDelivered(msg, errorCode);
//    }
//	}

}
