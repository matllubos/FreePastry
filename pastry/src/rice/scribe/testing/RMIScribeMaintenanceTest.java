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

package rice.scribe.testing;

import rice.pastry.*;
import rice.pastry.rmi.*;
import rice.pastry.standard.*;
import rice.pastry.join.*;
import rice.pastry.client.*;
import rice.pastry.messaging.*;
import rice.pastry.security.*;
import rice.pastry.routing.*;
import rice.pastry.leafset.*;

import rice.scribe.*;

import java.util.*;
import java.net.*;
import java.rmi.Naming;
import java.rmi.RMISecurityManager;
import java.rmi.RemoteException;
import java.security.*;

/**
 * @(#) RMIScribeMaintenanceTest.java
 *
 * a test suite for Scribe with RMI.
 *
 * @version $Id$
 *
 * @author Animesh Nandi
 * @author Atul Singh
 */

public class RMIScribeMaintenanceTest {
    private PastryNodeFactory factory;
    private Vector pastryNodes;
    private Random rng;
    public Vector rmiClients;
    private Timer m_timer;
    //private RMIMaintenanceEvent m_event;
    private int m_schedulingRate;
    private Vector localNodes;

    private static int port = 5009;
    private static String bshost = null;
    private static int bsport = 5009;
    private static int numNodes = 5;
    public Integer num = new Integer(0);
    public String publisherHost = "dosa.cs.rice.edu/128.42.3.76";
    // constructor

    public RMIScribeMaintenanceTest(int schedulingRate){
	factory = new RMIPastryNodeFactory(port);
	pastryNodes = new Vector();
	rmiClients = new Vector();
	rng = new Random(PastrySeed.getSeed());
	m_timer = new Timer(true);
	//m_event = new RMIMaintenanceEvent();
	m_schedulingRate = schedulingRate;
	localNodes = new Vector();
    }

    private NodeHandle getBootstrap() {
	RMIRemoteNodeI bsnode = null;
	try {
	    bsnode = (RMIRemoteNodeI)Naming.lookup("//:" + port + "/Pastry");
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
		bsnode = (RMIRemoteNodeI)Naming.lookup("//" + bshost
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
	// process command line arguments
	
	for (int i = 0; i < args.length; i++) {
	    if (args[i].equals("-help")) {
		System.out.println("Usage: RMIPASTRegrTest [-port p] [-bootstrap host[:port]] [-nodes n] [-help]");
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
		if (n > 0) numNodes = n;
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
    
    /**
     * This application spawns a thread for each virtual node. While this is
     * convenient, note that multithreadedness is not a requirement for RMI
     * applications, especially those with only one virtual node per host.
     * However, it _is_ essential for RMI applications to not send messages
     * till Node.isReady() becomes true (and app.notifyReady() gets called).
     *
     * This example demonstrates how to write HelloWorldApp in a wire
     * protocol independent fashion, catering to both multithreaded and
     * event-driven execution models.
     */

    private class ApplThread implements Runnable {

	private PastryNode pn;
	private RMIScribeMaintenanceTestApp m_app;
	private NodeHandle m_bootstrap;
	private RMIScribeMaintenanceTest m_driver;
	private int m_index = 0;
	private boolean ackOnSubscribe = true;
	/**
	 * Number of times to try joining, in case join messages are lost,
	 * and the retry timeout period.
	 */
	private static final int nJoinTries = 3, joinTimeout = 300 /* sec */;

	/**
	 * Constructor.
	 *
	 * @param n this pastrynode
	 * @param a application object
	 * @param bs bootstrap handle
	 */
	ApplThread(PastryNode n, RMIScribeMaintenanceTestApp a, NodeHandle bs, RMIScribeMaintenanceTest driver) {
	    pn = n;
	    m_app = a;
	    m_bootstrap = bs;
	    m_driver = driver;
	    m_index = m_driver.num.intValue();
	}

	public void run() {

	    // Do application specific stuff here.
	    System.out.println("I am up "+m_app.m_scribe.getNodeId());
	    NodeId topicId = generateTopicId(new String("Control Channel"));
	    
	    InetAddress localaddr = null;
	    String host = null;
	    
	    try {
		localaddr = InetAddress.getLocalHost();
	    } catch (UnknownHostException e) {
		System.out.println("[rmi] Error: Host unknown: " );
	    }
	    host = localaddr.toString();

	    if(m_app.m_appIndex == 0 && host.equals(publisherHost))
		m_app.create(topicId);
	    m_app.subscribe(topicId);
	    int count = 0;
	    while(true){
		if(  m_app.m_appIndex == 0 && host.equals(publisherHost)) {

		    m_app.publish(topicId, new Integer(m_app.m_seqno));
		    m_app.m_seqno ++;
		    pause(10*1000);
		}
		else {
		    pause(10*1000);
		    //m_app.processLog(topicId);
		}

		count ++;
		NodeHandle parent =  m_app.m_scribe.getTopic(topicId).getParent();
		//System.out.println("Parent for topic "+parent+" at node "+m_app.m_scribe.getNodeId());
		if(parent != null){
		    if( !m_driver.localNodes.contains(parent.getNodeId()))
			System.out.println("Yoooooo.. My "+m_app.m_scribe.getNodeId()+ " appindex=" + m_app.m_appIndex + "'s parent "+parent+"is in other machine");
		    //else
		    //System.out.println("My "+m_app.m_scribe.getNodeId()+" parent is in same machine");
		}
		m_app.m_scribe.scheduleHB();
	    }
	}
    }
    
    
    public NodeId generateTopicId( String topicName ) { 
	MessageDigest md = null;

	try {
	    md = MessageDigest.getInstance( "SHA" );
	} catch ( NoSuchAlgorithmException e ) {
	    System.err.println( "No SHA support!" );
	}

	md.update( topicName.getBytes() );
	byte[] digest = md.digest();
	
	NodeId newId = new NodeId( digest );
	
	return newId;
    }


    /**
     * Is this the first virtual node being created? If so, block till ready.
     */
    private static boolean firstvnode = true;
    
    /**
     * Create a Pastry node and add it to pastryNodes. Also create a client
     * application for this node, and spawn off a separate thread for it.
     */
    
    public void makeScribeNode() {
	NodeHandle bootstrap = getBootstrap();
	PastryNode pn = factory.newNode(bootstrap); // internally initiateJoins
	int joinTimeout = 300;
	pastryNodes.addElement(pn);
	//synchronized(localNodes){
	localNodes.addElement(pn.getNodeId());
	//}

	
	Credentials cred = new PermissiveCredentials();
	Scribe scribe = new Scribe(pn, cred );
	scribe.setTreeRepairThreshold(5);
	RMIScribeMaintenanceTestApp app = new RMIScribeMaintenanceTestApp(pn, scribe, cred);
	//m_event.addScribe(scribe);
	rmiClients.addElement(app);

	synchronized (pn) {
	    while (pn.isReady() == false) {
		try{
		    if (pn.isReady() == false)
			pn.wait(joinTimeout * 1000);
		} catch (InterruptedException e) { }
		if (pn.isReady() == false) System.out.println("retrying");
	    }
	}
	
	ApplThread thread = new ApplThread(pn, app, bootstrap, this);
	new Thread(thread).start();


    }

    /**
     * Start the maintenance sub-system.
     */
    public void startMaintenance(){
	//m_timer.scheduleAtFixedRate(m_event, 0, m_schedulingRate);
    }


    /**
     * Poz.
     *
     * @param ms milliseconds.
     */
    public synchronized void pause(int ms) {
	//if (Log.ifp(5)) System.out.println("waiting for " + (ms/1000) + " sec");
	try { wait(ms); } catch (InterruptedException e) {}
    }

    /**
     * Usage: RMIHelloWorld [-msgs m] [-nodes n] [-port p] [-bootstrap bshost[:bsport]]
     *                      [-verbose|-silent|-verbosity v] [-help].
     *
     * Ports p and bsport refer to RMI registry port numbers (default = 5009).
     * Without -bootstrap bshost[:bsport], only localhost:p is used for bootstrap.
     * Default verbosity is 5, -verbose is 10, and -silent is -1 (error msgs only).
     */
    public static void main(String args[]) {
	int seed;

	Log.init(args);
	doRMIinitstuff(args);
	//seed = -2127579971;
	seed = (int)System.currentTimeMillis();
	PastrySeed.setSeed(seed);
	System.out.println("seed used=" + seed); 
	RMIScribeMaintenanceTest driver = new RMIScribeMaintenanceTest(2*1000);
	int count = 0;

	for (int i = 0; i < numNodes; i++){
	    driver.makeScribeNode();
	    driver.pause(5*1000);
	}
	if (Log.ifp(5)) System.out.println(numNodes + " nodes constructed");
    }
}







