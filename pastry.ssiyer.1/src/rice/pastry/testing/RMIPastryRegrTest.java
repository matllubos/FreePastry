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
import rice.pastry.client.*;
import rice.pastry.messaging.*;
import rice.pastry.security.*;
import rice.pastry.routing.*;
import rice.pastry.leafset.*;

import java.util.*;
import java.net.*;
import java.rmi.Naming;
import java.rmi.RMISecurityManager;

/**
 * a regression test suite for pastry with RMI.
 *
 * @version $Id$
 *
 * @author andrew ladd
 * @author peter druschel
 * @author sitaram iyer
 */

public class RMIPastryRegrTest extends PastryRegrTest {
    private static int port;
    private static String bshost;
    private static int bsport;
    private static int numnodes;

    // constructor

    public RMIPastryRegrTest() {
	super();
	factory = new RMIPastryNodeFactory();
    }

    /**
     * Gets a handle to a bootstrap node. First tries localhost, to see
     * whether a previous virtual node has already bound itself. Then it
     * tries nattempts times on bshost:bsport.
     *
     * @return handle to bootstrap node, or null.
     */
    private RMINodeHandle getBootstrapHandle() {
	RMIPastryNode bsnode = null;
	try {
	    bsnode = (RMIPastryNode)Naming.lookup("//:" + port + "/Pastry");
	} catch (Exception e) {
	    System.out.println("Unable to find bootstrap node on localhost");
	}

	int nattempts = 3;

	// if bshost:bsport == localhost:port then nattempts = 0.
	// waiting for ourselves is not harmful, but pointless, and denies
	// others the usefulness of symmetrically waiting for us.

	if (bsport == port) {
	    InetAddress localaddr = null, connectaddr = null;
	    String host = null;

	    try {
		host = "localhost"; localaddr = InetAddress.getLocalHost();
		connectaddr = InetAddress.getByName(host = bshost);
	    } catch (UnknownHostException e) {
		System.out.println("[rmi] Error: Host unknown: " + host);
		nattempts = 0;
	    }

	    if (nattempts != 0 && localaddr.equals(connectaddr))
		nattempts = 0;
	}

	for (int i = 1; bsnode == null && i <= nattempts; i++) {
	    try {
		bsnode = (RMIPastryNode)Naming.lookup("//" + bshost
							 + ":" + bsport
							 + "/Pastry");
	    } catch (Exception e) {
		System.out.println("Unable to find bootstrap node on "
				   + bshost + ":" + bsport
				   + " (attempt " + i + "/" + nattempts + ")");
	    }

	    if (i != nattempts)
		pause(1000);
	}

	NodeId bsid = null;
	if (bsnode != null) {
	    try {
		bsid = bsnode.getNodeId();
	    } catch (Exception e) {
		System.out.println("[rmi] Unable to get remote node id: " + e.toString());
		bsnode = null;
	    }
	}

	RMINodeHandle bshandle = null;
	if (bsid != null)
	    bshandle = new RMINodeHandle(bsnode, bsid);

	return bshandle;
    }

    /**
     * process command line args, set the RMI security manager, and start
     * the RMI registry. Standard gunk that has to be done for all RMI apps.
     */
    private static void doRMIinitstuff(String args[]) {
	// defaults

	bshost = "thor01";
	port = bsport = 5009;
	numnodes = 1;

	// process command line arguments

	for (int i = 0; i < args.length; i++) {
	    if (args[i].equals("-help")) {
		System.out.println("Usage: RMIPastryTest [-port p] [-nodes n] [-bootstrap host[:port]] [-help]");
		System.exit(1);
	    }
	}
	
	for (int i = 0; i < args.length; i++) {
	    if (args[i].equals("-port") && i+1 < args.length) {
		int p = Integer.parseInt(args[i+1]);
		if (p > 0) port = p;
		break;
	    }
	}
	
	for (int i = 0; i < args.length; i++) {
	    if (args[i].equals("-bootstrap") && i+1 < args.length) {
		String str = args[i+1];
		int index = str.indexOf(':');
		if (index == -1) {
		    bshost = str;
		    bsport = port;
		} else {
		    bshost = str.substring(0, index);
		    bsport = Integer.parseInt(str.substring(index + 1));
		    if (bsport <= 0) bsport = port;
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

	// set RMI security manager

	if (System.getSecurityManager() == null)
	    System.setSecurityManager(new RMISecurityManager());

	// start RMI registry

	try {
	    java.rmi.registry.LocateRegistry.createRegistry(port);
	} catch (Exception e) {
	    System.out.println("Error starting RMI registry: " + e);
	}
    }

    // do nothing in the RMI world
    public boolean simulate() { return false; }

    public boolean simIsAlive(NodeId id) {
	// xxx
	return false;
    }

    protected void killNode(PastryNode pn) {
	// get to the RMIPastryNode somehow! how? hmm.. kill thread?
	// flag in PastryNode for this strange purpose?
	// or just pretend the node has died?
    }

    /**
     * Usage: RMIPastryTest [-port p] [-nodes n] [-bootstrap host[:port]] [-help]
     */

    public static void main(String args[]) {
	doRMIinitstuff(args);
	RMIPastryRegrTest pt = new RMIPastryRegrTest();
	mainfunc(pt, args);
    }
}
