package rice.bscribe.testing;

import rice.pastry.standard.*;
import rice.pastry.join.*;
import rice.pastry.client.*;
import rice.pastry.security.*;
import rice.pastry.messaging.*;
import rice.pastry.routing.*;
import rice.pastry.*;
import rice.pastry.direct.*;
import rice.pastry.leafset.*;

import java.util.*;
import java.security.*;

import rice.scribe.*;
import rice.scribe.messaging.*;

import rice.bscribe.*;
import rice.bscribe.messaging.*;

/**
 * @(#) BScribetest.java
 *
 * a simple test for BScribe.
 *
 * @version $Id$
 *
 * @author Animesh Nandi
 * @author Atul Singh
 */

public class BScribeTest {
    private DirectPastryNodeFactory factory;
    private NetworkSimulator simulator;

    private Vector pastryNodes;
    public Vector bscribeClients;
    public Hashtable nodeIdToApp;

    private Random rng;
    private int appCount = 0;
    
    public BScribeTest() {
	simulator = new EuclideanNetwork();
	factory = new DirectPastryNodeFactory(simulator);
	pastryNodes = new Vector();
	bscribeClients = new Vector();
	rng = new Random();
	nodeIdToApp = new Hashtable();
	
    }

    private NodeHandle getBootstrap() {
	NodeHandle bootstrap = null;
	try {
	    PastryNode lastnode = (PastryNode) pastryNodes.lastElement();
	    bootstrap = lastnode.getLocalHandle();
	} catch (NoSuchElementException e) {
	}
	return bootstrap;
    }

    public void makeBScribeNode(BScribeTest bt) {
	PastryNode pn = factory.newNode(getBootstrap());
	pastryNodes.addElement(pn);
	nodeIdToApp.put(pn.getNodeId(), new Integer(appCount));
	appCount ++;
	
	Credentials cred1 = new PermissiveCredentials();
	Scribe scribe = new Scribe(pn, cred1 );
	Credentials cred2 = new PermissiveCredentials();
	BScribe bscribe = new BScribe(bt, pn, scribe, cred2, BScribe.NUM_CHANNELS, BScribe.NUM_CHANNELS + 1);
	bscribeClients.addElement(bscribe);
    }

    public int rootApp(NodeId channelId) {
	int i;
	BScribe app;
	int rootApp = -1;

	for(i=0; i< bscribeClients.size() ; i++) {
	    app = ( BScribe) bscribeClients.elementAt(i);
	    if(app.isRoot(channelId)) {
		if(rootApp != -1)
		    System.out.println("Chaos::more than ONE app is root for a channel tree");
		else
		    rootApp = i; 
	    }
	}
	if(rootApp == -1)
	    System.out.println("Chaos::No app is root for a channel tree");
	return rootApp;
    }

    public void BFS(NodeId channelId) {
	int appIndex;
	Vector toTraverse = new Vector();
	BScribe app;
	Topic channelTopic;
	Set childrenSet;
	Vector children_clone;
	Iterator it;
	NodeId childId;
	int rootAppIndex;
	NodeHandle child;
	int i;
	int depth=0;

	rootAppIndex = rootApp(channelId);
	System.out.println("\n <<<<< TREE TRAVERSAL for Channel" + channelId + " BEGINS >>>>>>");
	toTraverse.add(new Integer(rootAppIndex));
	toTraverse.add(new Integer(-1));
	// This -1 acts as a marker a a level of children and thus helps to compute the depth
	while(toTraverse.size() > 0 ) {
	    appIndex = ((Integer)toTraverse.remove(0)).intValue();
	    if(appIndex == -1) {
		depth++;
		if(toTraverse.size() != 0)
		    toTraverse.add(new Integer(-1));
		continue;
	    }
	    app = (BScribe) bscribeClients.elementAt(appIndex);
	    System.out.println(" *** Children of " + app.getNodeId() + " ***");
	    channelTopic = app.m_scribe.getTopic(channelId);
	    childrenSet = channelTopic.getChildren();

	    if (!childrenSet.isEmpty()) {
		children_clone = new Vector();
		it = childrenSet.iterator();
		while(it.hasNext()) {
		    child = (NodeHandle)it.next();
		    children_clone.add(child);
		}
		for(i=0; i < children_clone.size(); i++) {
		    childId = ((NodeHandle)children_clone.elementAt(i)).getNodeId();
		    System.out.print(childId + " ");
		    appIndex = ((Integer)nodeIdToApp.get(childId)).intValue();
		    toTraverse.add(new Integer(appIndex));
		}
		System.out.println("");
	    }
	}
	System.out.println("TREE TRAVERSAL COMPLETE:: DEPTH = " + depth);
    }

    public boolean simulate() { 
	return simulator.simulate(); 
    }

    public static void main(String args[]) {
	BScribeTest bt = new BScribeTest();
	
	int n = 50;
	BScribe bscribe;
	Vector channelIds;
	Vector spareIds;
	int index;
	Random rng = new Random();
	BScribe app;
	int i;
	int j;
	int msgCount = 0;
	Date old = new Date();
	int msg_count = 0;

	// We will first form the Pastry network of all the nodes
	for (i=0; i<n; i++) {
	    bt.makeBScribeNode(bt);
	    while (bt.simulate());
	}
	while (bt.simulate());

	// We will first make the first node create all the topics required for the spare trees and the channel trees & then iteratively ask each node to subscribe to the set of trees
	for (i=0; i<n; i++) {
	    if(i==0) {
		channelIds = BScribe.getChannelIds();
		for(j=0 ; j< channelIds.size(); j++) {
		    app = (BScribe)bt.bscribeClients.elementAt(0);
		    app.create((NodeId)channelIds.elementAt(j));
		    while (bt.simulate());
		}
		System.out.println(" The topics corresponding to channel trees have been created");
		
		// We then create the topics corresponding to the spare trees
		spareIds = BScribe.getSpareIds();
		for(j=0 ; j< spareIds.size(); j++) {
		    app = (BScribe)bt.bscribeClients.elementAt(0);
		    app.create((NodeId)spareIds.elementAt(j));
		    while (bt.simulate());
		}
		System.out.println(" The topics corresponding to spare trees have been created");
		while (bt.simulate()) ;
	    }
	    // We first subscribe the new node to the spare tree
	    bscribe = (BScribe) bt.bscribeClients.elementAt(i);
	    bscribe.subscribeSpare();
	    while (bt.simulate()) ;
	    System.out.println("<<<" + bscribe.getNodeId() + "Subscribed to #spare tree");

	    /**** These 2 lines of code is added to ensure that the parent field of the nodes in the spare capacity tree is set */
	    bscribe.publish((NodeId)(BScribe.getSpareIds().elementAt(0)));
	    while (bt.simulate()) ;
	    /** 2 lines of code end here */

	    // We will now subscribe the new node to each of the channel trees
	    bscribe = (BScribe) bt.bscribeClients.elementAt(i);
	    channelIds = bscribe.m_channelIds;
	    for(j=0 ;j < channelIds.size(); j++) {
		bscribe.subscribeChannel((NodeId)channelIds.elementAt(j));
		while(bt.simulate());
		System.out.println("<<<" + bscribe.getNodeId() + "Subscribed to channel #" + j);
	    }
	    while (bt.simulate()) ;
	    System.out.println(" #####  Node "+i+ bscribe.getNodeId() + " subscribed to all channelIds and spareIds");
	    bt.printTrees();
	    System.out.println("\n\n\n");

	}
	
	// We will now publish a message to each of these channel topicIds and check if all the nodes receive each of these messages or not
	channelIds = BScribe.getChannelIds();
	for(i=0 ; i< channelIds.size(); i++) {
	    index = rng.nextInt(bt.bscribeClients.size());
	    app = (BScribe)bt.bscribeClients.elementAt(index);
	    app.publish((NodeId)channelIds.elementAt(i));
	}
	while(bt.simulate()) ;
	System.out.println(" We have published one message to each of the channel trees\n");

	

	// We now check to see if all the nodes received each of the messages
	for(i=0 ; i< bt.bscribeClients.size(); i++) {
	    app = (BScribe)bt.bscribeClients.elementAt(i);
	    System.out.println("BScribeApp[" + i + "]:totalmsg= " + app.totalmsg + ", load= " + app.m_load +" NodeId "+app.getNodeId());
	    
	    for(j=0; j< channelIds.size(); j++) {
		msg_count = ((Integer)app.message_count.get((NodeId)channelIds.elementAt(j))).intValue();
		if(msg_count != 1)
		    System.out.println("Message count for topic "+(NodeId)channelIds.elementAt(j)+" is "+ msg_count);  
	    }
	    
	}
    }

    public void printTrees() {
	Vector spareIds;
	Vector channelIds;
	int j;

	spareIds = BScribe.getSpareIds();
	for(j=0 ; j< spareIds.size(); j++) {
	    BFS((NodeId)spareIds.elementAt(j));
	}
	channelIds = BScribe.getChannelIds();
	for(j=0 ; j< channelIds.size(); j++) {
	    BFS((NodeId)channelIds.elementAt(j));
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

}


















