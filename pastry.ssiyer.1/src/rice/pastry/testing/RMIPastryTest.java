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

package rice.pastry.testing;

import rice.pastry.*;
import rice.pastry.rmi.*;
import rice.pastry.standard.*;
import rice.pastry.join.*;

import java.util.*;
import java.net.*;
import java.rmi.Naming;
import java.rmi.RMISecurityManager;

/**
 * Pastry test.
 *
 * A test case for pastry RMI. Each node currently starts one instance of
 * Pastry, but pt.makePastryNode can be called multiple times from main
 * (say for the benefit of different and incompatible p2p applications).
 *
 * @version $Id$
 *
 * @author sitaram iyer
 */

public class RMIPastryTest {
    private RMIPastryNodeFactory factory;
    private Vector pastrynodes;

    private static int port;
    private static String connecthost;
    private static int connectport;
    private static int numnodes;

    public RMIPastryTest() {
	factory = new RMIPastryNodeFactory();
	pastrynodes = new Vector();
    }

    public void makePastryNode() {

	RMIPastryNode other = null;
	try {
	    other = (RMIPastryNode)Naming.lookup("//:" + port + "/Pastry");
	    //pause(1000);
	} catch (Exception e) {
	    System.out.println("Unable to find another node on localhost");
	}

	int nattempts = 3;

	// if connecthost:connectport == localhost:port then nattempts = 0.
	// waiting for ourselves is not harmful, but pointless, and denies
	// others the usefulness of symmetrically waiting for us.

	if (connectport == port) {
	    InetAddress localaddr = null, connectaddr = null;
	    String host = null;

	    try {
		host = "localhost"; localaddr = InetAddress.getLocalHost();
		connectaddr = InetAddress.getByName(host = connecthost);
	    } catch (UnknownHostException e) {
		System.out.println("[rmi] Error: Host unknown: " + host);
		nattempts = 0;
	    }

	    if (nattempts != 0 && localaddr.equals(connectaddr))
		nattempts = 0;
	}

	for (int i = 1; other == null && i <= nattempts; i++) {
	    try {
		other = (RMIPastryNode)Naming.lookup("//" + connecthost
						    + ":" + connectport
						    + "/Pastry");
	    } catch (Exception e) {
		System.out.println("Unable to find another node on "
				   + connecthost + ":" + connectport
				   + " (attempt " + i + "/" + nattempts + ")");
	    }

	    if (i != nattempts)
		pause(1000);
	}

	/*
	 * This creates a PastryNode and an associated RMIPastryNode, and
	 * binds the latter to the registry. We do the above lookup prior to
	 * creating the node, so we don't find ourselves.
	 */
	PastryNode pn = new PastryNode(factory);
	pastrynodes.add(pn);
	System.out.println("created " + pn);

	NodeId otherid = null;
	if (other != null) {
	    try {
		otherid = other.getNodeId();
	    } catch (Exception e) {
		System.out.println("[rmi] Unable to get remote node id: " + e.toString());
		other = null;
	    }
	}

	if (otherid != null) {
	    RMINodeHandle other_handle = new RMINodeHandle(other, otherid /* , pn */);
	    pn.receiveMessage(new InitiateJoin(other_handle));
	}
	// else pause(1000);
    }

    public void printLeafSets() {
	pause(1000);
	for (int i = 0; i < pastrynodes.size(); i++)
	    System.out.println(((PastryNode)pastrynodes.get(i)).getLeafSet());
    }

    public synchronized void pause(int ms) {
	System.out.println("waiting for " + (ms/1000) + " sec");
	try { wait(ms); } catch (InterruptedException e) {}
    }

    /**
     * Usage: RMIPastryTest [-port n] [-connect host[:port]] [-nodes n]
     */
    public static void main(String args[])
    {
	// defaults
	connecthost = "thor05";
	port = connectport = 5009;
	numnodes = 1;

	for (int i = 0; i < args.length; i++) {
	    if (args[i].equals("-port") && i+1 < args.length) {
		int p = Integer.parseInt(args[i+1]);
		if (p > 0) port = p;
		break;
	    }
	}
	
	for (int i = 0; i < args.length; i++) {
	    if (args[i].equals("-connect") && i+1 < args.length) {
		String str = args[i+1];
		int index = str.indexOf(':');
		if (index == -1) {
		    connecthost = str;
		    connectport = port;
		} else {
		    connecthost = str.substring(0, index);
		    connectport = Integer.parseInt(str.substring(index + 1));
		    if (connectport <= 0) connectport = port;
		}
		break;
	    }
	}
	
	for (int i = 0; i < args.length; i++) {
	    if (args[i].equals("-nodes") && i+1 < args.length) {
		int n = Integer.parseInt(args[i+1]);
		if (n > 0) numnodes = n;
		break;
	    }
	}

	RMIPastryTest pt = new RMIPastryTest();

	if (System.getSecurityManager() == null)
	    System.setSecurityManager(new RMISecurityManager());

	try {
	    java.rmi.registry.LocateRegistry.createRegistry(port);
	} catch (Exception e) {
	    System.out.println("Error starting RMI registry: " + e);
	}

	for (int i = 0; i < numnodes; i++)
	    pt.makePastryNode();

	while (true)
	    pt.printLeafSets();
    }
}
