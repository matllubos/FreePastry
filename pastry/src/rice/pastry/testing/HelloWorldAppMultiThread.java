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

package rice.pastry.testing;

import rice.pastry.*;
import rice.pastry.direct.*;
import rice.pastry.standard.*;
import rice.pastry.join.*;
import rice.pastry.client.*;
import rice.pastry.security.*;
import rice.pastry.messaging.*;
import rice.pastry.routing.*;

import java.util.*;
import java.io.*;

/**
 * A hello world example for pastry. This is the per-node app object.
 *
 * @version $Id$
 *
 * @author Sitaram Iyer
 */

public class HelloWorldAppMultiThread extends PastryAppl {

    private int msgid = 0;

    private static Address addr = new HelloAddress();
    private static Credentials cred = new PermissiveCredentials();

    private static class HelloAddress implements Address {
	private int myCode = 0x1984abcd;
	
	public int hashCode() { return myCode; }

	public boolean equals(Object obj) {
	    return (obj instanceof HelloAddress);
	}

	public String toString() { return "[HelloAddress]"; }
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
	    System.out.println("Sending message from " + getNodeId() + " to random dest " + rndid);
	Message msg = new HelloMsg(addr, getNodeId(), rndid, ++msgid);
	routeMsg(rndid, msg, cred, new SendOptions());
    }
    
    /**
     * Sends a message to a randomly chosen node. Yeah, for fun.
     *
     * @param rng Random number generator.
     */
    public void sendRndMsg(Random rng, int id) {
	Id rndid = Id.makeRandomId(rng);
	if (Log.ifp(5)) {
//	    System.out.println("Sending message # " + id + " from " + getNodeId() + " to random dest " + rndid);
    }
	HelloMsg msg = new HelloMsg(addr, getNodeId(), rndid, id);
        routeMsg(rndid, msg, cred, new SendOptions());
        driver.MessageSent(msg);        
    }

    // The remaining methods override abstract methods in the PastryAppl API.

    /**
     * Get address.
     *
     * @return the address of this application.
     */
    public Address getAddress() { return addr; }

    /**
     * Get credentials.
     *
     * @return credentials.
     */
    public Credentials getCredentials() { return cred; }

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
                    if(msg instanceof HelloMsg){
                        HelloMsg hmsg = (HelloMsg)msg;
                        hmsg.actualReceiver = getNodeId();
                        driver.MessageRecieved(hmsg);
                        //if (Log.ifp(5))
                            //System.out.println("Received " + msg + " at " + getNodeId());
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
    public boolean enrouteMessage(Message msg, NodeId key, NodeId nextHop, SendOptions opt) {
	//if (Log.ifp(5))
	    //System.out.println("Enroute " + msg + " at " + getNodeId());
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
	    System.out.println("Node " + getNodeId() + " ready, waking up any clients");
	//sendRndMsg(new Random());

    }
   
    //For the purpose of global bookKeeping, we are allowing
    //every PastryAppl access to the driver
    private DistHelloWorldMultiThread driver;
    
   
    public HelloWorldAppMultiThread(PastryNode pn, DistHelloWorldMultiThread drv) {
	super(pn);
        driver = drv;
        driver.node_created();

    }
}


