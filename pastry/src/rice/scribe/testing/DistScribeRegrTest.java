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
import rice.pastry.dist.*;

import rice.scribe.*;
import rice.scribe.maintenance.*;

import java.util.*;
import java.net.*;
import java.rmi.Naming;
import java.rmi.RMISecurityManager;
import java.rmi.RemoteException;
import java.security.*;

/**
 * @(#) DistScribeRegrTest.java
 *
 * A test suite for Scribe with RMI/WIRE.
 *
 * @version $Id$
 *
 * @author Animesh Nandi
 * @author Atul Singh
 */

public class DistScribeRegrTest {
    private PastryNodeFactory factory;
    private Vector pastryNodes;
    private Random rng;
    public Vector distClients;
    private Vector localNodes;

    private static int port = 5009;
    private static String bshost = null;
    private static int bsport = 5009;
    private static int numNodes = 5;
    public Integer num = new Integer(0);
    public static int NUM_TOPICS = 1;
    public static int UNSUBSCRIBE_LIMIT = 5;
    public static int numUnsubscribed = 0;
    public static double fractionUnsubscribedAllowed = 0.5; 
    public static Object LOCK = new Object();
    public static int IDLE_TIME = 50; // in seconds
    public static int PROTOCOL = DistPastryNodeFactory.PROTOCOL_WIRE;

    public DistScribeRegrTest(){
	factory = DistPastryNodeFactory.getFactory(PROTOCOL, port);
	pastryNodes = new Vector();
	distClients = new Vector();
	rng = new Random(PastrySeed.getSeed());
	localNodes = new Vector();
    }

    private NodeHandle getBootstrap() {
	InetSocketAddress addr = new InetSocketAddress(bshost, bsport);
	NodeHandle bshandle = ((DistPastryNodeFactory)factory).getNodeHandle(addr);
	return bshandle;
    }

    /**
     * process command line args, set the security manager, and start
     * the RMI registry if using RMI protocol. 
     */
    private static void doInitstuff(String args[]) {
	// process command line arguments
	
	for (int i = 0; i < args.length; i++) {
	    if (args[i].equals("-help")) {
		System.out.println("Usage: DistScribeRegrTest [-port p] [-bootstrap host[:port]] [-nodes n] [-help]");
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
	
	if( PROTOCOL == DistPastryNodeFactory.PROTOCOL_RMI){

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
    }
    
    /**
     * This application spawns a thread for each virtual node. While this is
     * convenient, note that multithreadedness is not a requirement for Distributed
     * applications, especially those with only one virtual node per host.
     * However, it _is_ essential for RMI/WIRE applications to not send messages
     * till Node.isReady() becomes true (and app.notifyReady() gets called).
     *
     * This example demonstrates how to write HelloWorldApp in a wire
     * protocol independent fashion, catering to both multithreaded and
     * event-driven execution models.
     */

    private class ApplThread implements Runnable {

	private PastryNode pn;
	private DistScribeRegrTestApp m_app;
	private DistScribeRegrTest m_driver;
	/**
	 * Constructor.
	 *
	 * @param n this pastrynode
	 * @param a application object
	 */
	ApplThread(PastryNode n, DistScribeRegrTestApp a, DistScribeRegrTest driver) {
	    pn = n;
	    m_app = a;
	    m_driver = driver;
	}

	public void run() {
	    int i;
	    NodeId topicId;
	    DistTopicLog topicLog;
	    Vector topics = new Vector();
	    Random rng = new Random(PastrySeed.getSeed() + m_app.m_appIndex);
	    // Do application specific stuff here.
	    System.out.println("I am up "+m_app.m_scribe.getNodeId());
	   
	    int publish_period = DistScribeMaintenanceThread.m_maintPeriod;
	    int threshold = m_app.m_scribe.getTreeRepairThreshold();
	    int seq_num = -1;
	    int count = 1;
	    int lastRecv;
	    for (i=0; i< DistScribeRegrTest.NUM_TOPICS; i++) {
		topicId = generateTopicId(new String("Topic " + i));
		topics.add(topicId);
		m_app.m_logTable.put(topicId, new DistTopicLog());
	     }

	    for (i=0; i< DistScribeRegrTest.NUM_TOPICS; i++) {
		topicId = (NodeId)topics.elementAt(i);
		m_app.create(topicId);
		m_app.subscribe(topicId);
	     }

	    while(true){
		for (i=0; i< DistScribeRegrTest.NUM_TOPICS; i++) {
		    topicId = (NodeId) topics.elementAt(i);
		    topicLog = (DistTopicLog) m_app.m_logTable.get(topicId);
		    seq_num = topicLog.getSeqNumToPublish();
		    count = topicLog.getCount();
		    if( m_app.m_scribe.isRoot(topicId)){
			m_app.publish(topicId, new Integer(seq_num));
			/*
			 * We play safe in publishing the '-1' so that all nodes
			 * which were doing a tree repair while the new root
			 * came up can still see the demarcation of '-1'
			 */
			if(count < threshold*2){
			    count++;
			}
			else
			    seq_num ++;
		    }
		    else {
			count = 1;
			seq_num = -1;
		    }
		    topicLog.setCount(count);
		    topicLog.setSeqNumToPublish(seq_num);

		    /* We unsubscribe with a probability of 0.1 after we have received
		     * a sequence number 'UNSUBSCRIBE_LIMIT' for a topic.
		     */
		    int allowed = (int)( DistScribeRegrTest.fractionUnsubscribedAllowed * m_driver.localNodes.size());
		    synchronized( DistScribeRegrTest.LOCK){
			if( DistScribeRegrTest.numUnsubscribed < allowed){
			    if(! topicLog.getUnsubscribed()){
				lastRecv = topicLog.getLastSeqNumRecv();
				if(lastRecv > DistScribeRegrTest.UNSUBSCRIBE_LIMIT){
				    int n = rng.nextInt(10);
				    if( n == 0){
					m_app.unsubscribe(topicId);
					DistScribeRegrTest.numUnsubscribed ++;
				    }
				}
			    }
			}
		    }

		    
		    if(! topicLog.getUnsubscribed()){
			long currentTime = System.currentTimeMillis();
			long prevTime = topicLog.getLastRecvTime();
			int diff = (int)((currentTime - prevTime)/ 1000.0);

			if( diff > DistScribeRegrTest.IDLE_TIME)
			    System.out.println("\nWARNING :: "+m_app.m_scribe.getNodeId()+" DID NOT  Receive a message on the topic "+topicId + " for "+diff+" secs \n");
			
		    }


		    if(! topicLog.getUnsubscribed()){
			NodeHandle parent =  m_app.m_scribe.getTopic(topicId).getParent();
			
			if(parent != null){
			    if( !m_driver.localNodes.contains(parent.getNodeId()))
				System.out.println("Yoooooo.. My "+m_app.m_scribe.getNodeId()+ " appindex=" + m_app.m_appIndex + "'s parent "+parent+"is in other machine");
			}
			
		    }
		}
		pause(publish_period*1000);
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
     * Create a Pastry node and add it to pastryNodes. Also create a client
     * application for this node, and spawn off a separate thread for it.
     */
    public void makeScribeNode() {
	NodeHandle bootstrap = getBootstrap();
	PastryNode pn = factory.newNode(bootstrap); // internally initiateJoins
	int joinTimeout = 300;
	pastryNodes.addElement(pn);
	localNodes.addElement(pn.getNodeId());
	

	
	Credentials cred = new PermissiveCredentials();
	Scribe scribe = new Scribe(pn, cred );
	scribe.setTreeRepairThreshold(3);
	DistScribeRegrTestApp app = new DistScribeRegrTestApp(pn, scribe, cred);
	distClients.addElement(app);

	synchronized (pn) {
	    while (pn.isReady() == false) {
		try{
		    if (pn.isReady() == false)
			pn.wait(joinTimeout * 1000);
		} catch (InterruptedException e) { }
		if (pn.isReady() == false) System.out.println("retrying");
	    }
	}
	
	ApplThread thread = new ApplThread(pn, app, this);
	new Thread(thread).start();


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
     * Usage: DistScribeRegrTest [-nodes n] [-port p] [-bootstrap bshost[:bsport]]
     *                      [-help].
     *
     * Ports p and bsport refer to WIRE/RMI port numbers (default = 5009).
     * Without -bootstrap bshost[:bsport], only localhost:p is used for bootstrap.
     */
    public static void main(String args[]) {
	int seed;

	Log.init(args);
	doInitstuff(args);
	//seed = -1089549604;
	seed = (int)System.currentTimeMillis();
	PastrySeed.setSeed(seed);
	System.out.println("seed used=" + seed); 
	DistScribeRegrTest driver = new DistScribeRegrTest();
	int count = 0;

	for (int i = 0; i < numNodes; i++){
	    driver.makeScribeNode();
	    driver.pause(5*1000);
	}
	if (Log.ifp(5)) System.out.println(numNodes + " nodes constructed");
    }
}







