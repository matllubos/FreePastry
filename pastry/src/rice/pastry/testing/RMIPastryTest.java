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

import java.util.*;
import java.net.*;
import java.rmi.Naming;
import java.rmi.RMISecurityManager;
import java.rmi.RemoteException;

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
    private PastryNodeFactory factory;
    private Vector pastrynodes;

    private static int port = 5009;
    private static String bshost = null;
    private static int bsport = 5009;
    private static int numnodes = 1;

    public RMIPastryTest() {
	factory = new RMIPastryNodeFactory(port);
	pastrynodes = new Vector();
    }

    /**
     * Gets a handle to a bootstrap node. First we try localhost, to see
     * whether a previous virtual node has already bound itself there.
     * Then we try nattempts times on bshost:bsport. Then we fail.
     *
     * @return handle to bootstrap node, or null.
     */
    protected NodeHandle getBootstrap() {
	RMIRemoteNodeI bsnode = null;

	try {
	    bsnode = (RMIRemoteNodeI)Naming.lookup("//:" + port + "/Pastry");
	    if (bsnode != null) if (Log.ifp(5)) System.out.println("Bootstrapping from localhost:" + port);
	} catch (Exception e) {
	    if (Log.ifp(5)) System.out.println("Unable to find bootstrap node on localhost");
	}

	if (bshost == null && bsnode == null) {
	    if (Log.ifp(5)) System.out.println("Not using any bootstrap node");
	    return null;
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
		System.out.println("Error: Host unknown: " + host);
		nattempts = 0;
	    }

	    if (nattempts != 0 && localaddr.equals(connectaddr))
		nattempts = 0;
	}

	for (int i = 1; bsnode == null && i <= nattempts; i++) {
	    try {
		bsnode = (RMIRemoteNodeI)Naming.lookup("//" + bshost
							 + ":" + bsport
							 + "/Pastry");
		if (bsnode != null) if (Log.ifp(5)) System.out.println("Bootstrapping from " + bshost + ":" + bsport);
	    } catch (Exception e) {
		if (Log.ifp(5))
		    System.out.println("Unable to find bootstrap node on "
				       + bshost + ":" + bsport
				       + " (attempt " + i + "/" + nattempts + ")");
	    }

	    if (bsnode == null && i != nattempts)
		pause(1000);
	}

	NodeId bsid = null;
	if (bsnode != null) {
	    try {
		bsid = bsnode.getNodeId();
	    } catch (RemoteException e) {
		if (Log.ifp(5)) System.out.println("Unable to get remote node id: " + e.toString());
		bsnode = null;
	    }
	}

	RMINodeHandle bshandle = null;
	if (bsid != null)
	    bshandle = new RMINodeHandle(bsnode, bsid);

	if (bsnode == null) if (Log.ifp(5)) System.out.println("Not using any bootstrap node");

	return bshandle;
    }

    /**
     * process command line args, set the RMI security manager, and start
     * the RMI registry. Standard gunk that has to be done for all RMI apps.
     */
    private static void doRMIinitstuff(String args[]) {
	// process command line arguments

	for (int i = 0; i < args.length; i++) {
	    if (args[i].equals("-help")) {
		System.out.println("Usage: RMIPastryTest [-msgs m] [-nodes n] [-port p] [-bootstrap bshost[:bsport]]");
		System.out.println("                     [-verbose|-silent|-verbosity v] [-help]");
		System.out.println("");
		System.out.println("  Ports p and bsport refer to RMI registry port numbers (default = 5009).");
		System.out.println("  Without -bootstrap bshost[:bsport], only localhost:p is used for bootstrap.");
		System.out.println("  Default verbosity is 5, -verbose is 10, and -silent is -1 (error msgs only).");
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
	} catch (RemoteException e) {
	    System.out.println("Error starting RMI registry: " + e);
	}
    }

    /**
     * Is this the first virtual node being created? If so, block till ready.
     */
    private static boolean firstvnode = true;

    public void makePastryNode() {
	// or, for a sweet one-liner,
	// pastrynodes.add(new RMIPastryNode(factory, getBootstrap()));

	PastryNode pn = factory.newNode(getBootstrap());
	pastrynodes.add(pn);
	if (Log.ifp(5)) System.out.println("created " + pn);

	// the first virtual node blocks till ready.
	if (firstvnode) {
	    firstvnode = false;
	    if (Log.ifp(6)) System.out.println("blocking until first virtual node is ready");
	    while (pn.isReady() == false) {
		synchronized (pn) {
		    if (pn.isReady() == false)
			try { pn.wait(); } catch (InterruptedException e) { }
		}
	    }
	    if (Log.ifp(6)) System.out.println("first virtual node is ready");
	}
    }

    public void printLeafSets() {
	pause(1000);
	for (int i = 0; i < pastrynodes.size(); i++)
	    if (Log.ifp(5)) System.out.println(((PastryNode)pastrynodes.get(i)).getLeafSet());
    }

    public synchronized void pause(int ms) {
	if (Log.ifp(5)) System.out.println("waiting for " + (ms/1000) + " sec");
	try { wait(ms); } catch (InterruptedException e) {}
    }

    /**
     * Usage: RMIPastryTest [-msgs m] [-nodes n] [-port p] [-bootstrap bshost[:bsport]]
     *                      [-verbose|-silent|-verbosity v] [-help].
     *
     * Ports p and bsport refer to RMI registry port numbers (default = 5009).
     * Without -bootstrap bshost[:bsport], only localhost:p is used for bootstrap.
     * Default verbosity is 5, -verbose is 10, and -silent is -1 (error msgs only).
     */
    public static void main(String args[]) {
	Log.init(args);
	doRMIinitstuff(args);
	RMIPastryTest pt = new RMIPastryTest();

	for (int i = 0; i < numnodes; i++)
	    pt.makePastryNode();

	while (true) pt.printLeafSets();
    }
}
