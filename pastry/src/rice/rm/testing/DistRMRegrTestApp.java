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

package rice.rm.testing;

import rice.pastry.*;
import rice.pastry.client.*;
import rice.pastry.security.*;
import rice.pastry.routing.*;
import rice.pastry.messaging.*;
import rice.pastry.direct.*;
import rice.pastry.leafset.*;

import rice.rm.*;
import rice.rm.messaging.*;
import rice.rm.testing.*;

import java.util.*;
import java.security.*;
import java.io.*;

/**
 * @(#) DistRMRegrTestApp.java
 *
 * an application used by the DistRMRegrTest  suite for Replica Manager
 * @version $Id$
 * @author Animesh Nandi 
 */

public class DistRMRegrTestApp extends PastryAppl implements RMClient
{
    protected PastryNode m_pastryNode ;
    public RMImpl m_rm;
    public int m_appIndex;
    public static int m_appCount = 0;


    /**
     * The receiver address for the DistRMRegrTestApp system.
     */
    protected static Address m_address = new DistRMRegrTestAppAddress();

    /**
     * The SendOptions object to be used for all messaging through Pastry
     */
    protected SendOptions m_sendOptions = null;

    /**
     * The Credentials object to be used for all messaging through Pastry
     */
    protected static Credentials m_credentials = null;
    
    //  this determines the frequency with which the DistRMRegrTest message is 
    // delivered to this testerapplication.
    private int m_testFreq ;

    public Hashtable m_objects;

    public Vector m_objectKeys;

    public static final int m_numObjects = 5;

    public static final int m_replicaFactor = 4; 

    // checkpassed is used to check the system's invariants with regard to 
    // position of replicas. It can be set to false either in refresh() or check() method
    public boolean checkpassed = true;

    public boolean m_firstNodeInSystem;

    public static final int STALE = 5;

    public static final int MISSING = 5;

    // This will be used only by the first node in the system which
    // will initially replicate() and then later periodically hearbeat()
    public boolean replicationDone = false;

    public int numReplicated = 0;

    public int numRefreshed = 0;

    private int m_ReplicateRefreshPeriod = 15;

    private int m_checkPeriod;

    private static class DistRMRegrTestAppAddress implements Address {
	private int myCode = 0x8abc848c;
	
	public int hashCode() { return myCode; }

	public boolean equals(Object obj) {
	    return (obj instanceof DistRMRegrTestAppAddress);
	}
    }

 

    public DistRMRegrTestApp( PastryNode pn, RMImpl rm, Credentials cred, boolean firstNodeInSystem) {
	super(pn);
	m_firstNodeInSystem = firstNodeInSystem;
	m_rm = rm;
	m_credentials = cred;
	if(cred == null) {
	    m_credentials = new PermissiveCredentials();
	}
	m_pastryNode = pn;
	m_sendOptions = new SendOptions();
	m_appIndex = m_appCount ++;

	// This sets the periodic rate at which the DistRMRegrTest Messages will be 
	// invoked.
	m_testFreq = DistRMRegrTest.rmMaintFreq;
	m_objects = new Hashtable();
	m_checkPeriod = m_numObjects * m_ReplicateRefreshPeriod;

	NodeId objectKey;
	m_objectKeys = new Vector();
	for(int i=0; i< m_numObjects; i ++) {
	    objectKey = generateTopicId( new String( "Object" + i ) );
	    m_objectKeys.add(objectKey);
	    m_objects.put(objectKey, new ObjectState(false, 0, 0));
	}

	// This is done at the end of the constructor so that all the 
	// variables are initialized before we get the call to rmIsReady()
	m_rm.register(m_address,this);
    }


    // Procedures for the upcalls from the underlying replica manager



    public void rmIsReady() {
	 System.out.println("I am up " + getNodeId());
	 if(m_firstNodeInSystem) {
	     // Trigger the invokation of DistRMRegrTestReplicate message
	     m_pastryNode.scheduleMsgAtFixedRate(makeDistRMRegrTestReplicateMessage(m_credentials), 60*1000, 15*1000);
	 }
	     

	 // Trigger the periodic invokation of DistRMRegrTest message
	 m_pastryNode.scheduleMsgAtFixedRate(makeDistRMRegrTestMessage(m_credentials), (120 + 2* m_checkPeriod)*1000 , m_checkPeriod*1000);
    }
    

    // Upcall from replica manager
    public void responsible(NodeId objectKey, Object object) {
	ObjectState state;

	//System.out.println("responsible() called on node" + getNodeId());
	state = (ObjectState)m_objects.get(objectKey);
	if(state.isPresent()) {
	    // object exists already, so this represents a superfluous message
	    System.out.println("WARNING: responsible() called on " + getNodeId() + " for object " + objectKey + " that aleady existed");
	}
	else {
	    // we add this object to our objects hashtable, and set its refresh count
	    // as 1. 
	    state.setPresent(true);
	    state.setstaleCount(0);

	}
    }

    // Upcall from replica manager
    public void notresponsible(NodeId objectKey) {
	ObjectState state;

	//System.out.println("notresponsible() called on node" + getNodeId());
	state = (ObjectState)m_objects.get(objectKey);

	if(!state.isPresent()) {
	    // object does not exist, so this represents a superfluous message
	    System.out.println("WARNING: notresponsible() called on " + getNodeId() + " for object " + objectKey + " that did not exist");
	}
	else {
	    // we remove this object from our objects hashtable
	    state.setPresent(false);
	    state.setmissingCount(0);

	}
    }
    

    // Upcall from replica manager
    public void refresh(NodeId objectKey) {
	ObjectState state;

	//System.out.println("refresh() called on node" + getNodeId());
	state = (ObjectState)m_objects.get(objectKey);

	if(!state.isPresent()) {
	    // object does not exists, so this represents a error 
	    System.out.println("ERROR: refresh() called on " + getNodeId() + " for object " + objectKey + " that did not exist");
	    state.incrMissingCount();
	}
	else {
	    // we reset the refreshCount of the object
	    state.setstaleCount(0);
	}
    }


    // Call to underlying replica manager
    public void replicate(NodeId objectKey, int replicaFactor) {
	m_rm.replicate(getAddress(),objectKey, null,replicaFactor);
    }

    // Call to underlying replica manager
    public void heartbeat(NodeId objectKey, int replicaFactor) {
	m_rm.heartbeat(getAddress(),objectKey,replicaFactor);
    }

    // Call to underlying replica manager
    public void remove(NodeId objectKey, int replicaFactor) {
	m_rm.remove(getAddress(), objectKey, replicaFactor);
    }

    


    public Credentials getCredentials() { 
	return m_credentials;
    }
    

    public Address getAddress() {
	return m_address;
    }

    public void messageForAppl(Message msg) {
	if(msg instanceof DistRMRegrTestMessage) {
	    DistRMRegrTestMessage tmsg = (DistRMRegrTestMessage)msg;
	    tmsg.handleDeliverMessage( this);
	}
	if(msg instanceof DistRMRegrTestReplicateMessage) {
	    DistRMRegrTestReplicateMessage tmsg = (DistRMRegrTestReplicateMessage)msg;
	    tmsg.handleDeliverMessage( this);
	}
    }

    /**
     * Makes a DistRMRegrTest message.
     *
     * @param c the credentials that will be associated with the message
     * @return the DistRMRegrTestMessage
     */
    private Message makeDistRMRegrTestMessage(Credentials c) {
	return new DistRMRegrTestMessage( m_address, c );
    }


    /**
     * Makes a DistRMRegrTestReplicate message.
     *
     * @param c the credentials that will be associated with the message
     * @return the DistRMRegrTestReplicateMessage
     */
    private Message makeDistRMRegrTestReplicateMessage(Credentials c) {
	return new DistRMRegrTestReplicateMessage( m_address, c );
    }




    /*
    // This function will be invoked to check if the refreshCount of all the objects
    public boolean check() {
	Enumeration keys = m_objects.keys();	
	ObjectState state;
	NodeId objectKey;

	while(keys.hasMoreElements()){
	    objectKey = (NodeId)keys.nextElement();
	    state = (ObjectState)m_objects.get(objectKey);
	    if(state.getrefreshCount() >= STALE) {
		System.out.println("ERROR: Node " + getNodeId() + " holds object " + objectKey + " when it should not");
		checkpassed = false;
	    }

	}
	return checkpassed;

    }
    
    */


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








