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


package rice.rm.testing;

import rice.pastry.*;
import rice.pastry.routing.*;
import rice.pastry.client.*;
import rice.pastry.messaging.*;

import rice.pastry.security.*;
import rice.rm.*;
import rice.rm.messaging.*;
import rice.pastry.direct.*;

import java.util.*;
import java.io.*;

/**
 *
 * @version $Id$
 * @author Animesh Nandi 
 */

public abstract class RMRegrTestApp extends CommonAPIAppl implements RMClient 
{

    protected PastryNode m_pastryNode ;
    private Credentials _credentials;
    public SendOptions _sendOptions;
    public RMImpl m_rm;
    public int m_appCount;
    //public Hashtable m_objects;

    // This should coorespond to the keys that this node is exactly 
    // responsile for
    public IdSet m_keys;

    // This is the set of keys for which this node receives a refresh message
    public IdSet m_refreshedKeys;

    // This is the number of additional replicas
    public static int rFactor = 4;

    public Hashtable m_pendingObjects;

    public static class ReplicateEntry{
	private int numAcks;
	private Object object;
	
	public ReplicateEntry(Object _object){
	    this.numAcks = 0;
	    this.object = _object ;
	}
	public int getNumAcks(){
	    return numAcks;
	}

	public Object getObject() {
	    return object;
	}

	public void incNumAcks() {
	    numAcks ++;
	    return;
	}
    }

    /**
     * Constructor
     * @param node The local PastryNode
     * @param rm The underlying replica manager 
     * @param cred The credentials 
     */
    public RMRegrTestApp( PastryNode pn, Credentials cred, String instance) {
	super(pn, instance);
	_credentials = cred;
	if(cred == null) {
	     _credentials = new PermissiveCredentials();
	}
	_sendOptions = new SendOptions();
	m_pastryNode = pn;
	//m_objects = new Hashtable();

	m_keys = new IdSet(); 
	m_refreshedKeys = new IdSet();
	m_pendingObjects = new Hashtable();
    }


    public void rmIsReady(RM rm) {
	m_rm = (RMImpl)rm;
    }

    public ReplicateEntry getPendingObject(Id key) {
	return (ReplicateEntry)m_pendingObjects.get(key);
    }

    public void removePendingObject(Id key) {
	m_pendingObjects.remove(key);
    }
    


    public void printRange() {
	IdRange myRange1;
	myRange1 = range(getLocalHandle(), rFactor, getNodeId(), true);
	System.out.println("Range on " + getNodeId() + " is " + myRange1);

    }


    /**
     * Called by the application when it needs to replicate an object into
     * k nodes closest to the object key.
     *
     * @param objectKey  the pastry key for the object
     * @param object the object
     * @return true if operation successful else false
     */
    public boolean replicate(Id objectKey) {
	Object object = new Integer(1);
	m_pendingObjects.put(objectKey, new ReplicateEntry(object));
	ReplicateMsg msg = new ReplicateMsg(getLocalHandle(),getAddress(),objectKey, getCredentials());
	route(objectKey, msg, null);
	return true;

    }



    public void replicateSuccess(Id key, boolean status) {
	if(status)
	    System.out.println("Object " + key + " successfully replicated");
	else
	    System.out.println("Object " + key + " unsuccessfully replicated");
    }

    public void remove(Id objectKey) {

    }


    public void fetch(IdSet keySet) {
	// We will add these keys to m_keys
	 Iterator it = keySet.getIterator();
	 //System.out.println("fetch called for " + keySet.numElements() + " keys");
	 while(it.hasNext()) {
	     Id key = (Id)it.next();
	     boolean isMember;
	     isMember = m_keys.isMember(key);
	     if(!isMember) {
		 System.out.println(getNodeId() + " asked to fetch key " + key); 
		 m_keys.addMember(key);
	     }
	 }
    }

    // Upcalls to be implemented
    public void store(Id key, Object object) {
	//System.out.println(getNodeId() + " asked to store object " + key);
	m_keys.addMember(key);

    }

    // This upcall should return the set of keys that the application
    // currently stores in this range
    
    public IdSet scan(IdRange range) {
	IdSet subset = new IdSet();

	// We will go through the m_keys set and add those keys
	// that do not lie within this range

	// We will later improve this implementaion to use the subset
	// method in SortedSet
	Iterator it = m_keys.getIterator();
	while(it.hasNext()) {
	    Id key = (Id)it.next();
	    Id ccw = range.getCCW();
	    Id cw = range.getCW();
	    if(key.isBetween(ccw, cw)) {
		subset.addMember(key);
	    }
	}
	    
	return subset;
    }
    

    public void isResponsible(IdRange range) {
	// We will go through the m_keys set and remove those keys
	// that do not lie within this range

	//System.out.println("isResponsible called on " + getNodeId() + " with range= " + range + range.isEmpty());
	
	Vector toRemove = new Vector();
	Iterator it = m_keys.getIterator();
	while(it.hasNext()) {
	    Id key = (Id)it.next();
	    Id ccw = range.getCCW();
	    Id cw = range.getCW();
	    //if(!key.isBetween(ccw, cw)) {
	    if(!range.contains(key)) {
		//System.out.println("ccw= " + ccw + " cw= " + cw + " key= " + key);
		toRemove.add(key);
	    }
	}
	    
	for(int i=0; i<toRemove.size(); i++) {
	    Id key = (Id)toRemove.elementAt(i);
	    m_keys.removeMember(key);
	    System.out.println(getNodeId() + " no longer responsible for key " + key);
	}


    }
    

    public Credentials getCredentials() {
	return _credentials;
    }


    //public void messageForAppl(Message msg) {
    public void deliver(Id key, Message msg) {
	TestMessage tmsg = (TestMessage)msg;
	tmsg.handleDeliverMessage(this);

    }




    public NodeHandle getLocalHandle() {
	return thePastryNode.getLocalHandle();
    }


     // This will be used to refresh the objects
    public void heartbeat(Id objectKey) {
	//System.out.println(getNodeId() + " refreshing object " + objectKey);
	HeartbeatMsg msg = new HeartbeatMsg(getLocalHandle(),getAddress(),objectKey, getCredentials());
	
	this.route(objectKey, msg, null);
    }
    
    public void refresh(Id objectKey) {
	//System.out.println(getNodeId() + " asked to refresh key " + objectKey);
	m_refreshedKeys.addMember(objectKey);

    }

    // Call to underlying replica manager
    public void periodicMaintenance() {
	//System.out.println(getNodeId() + " issuing periodic maintenance ");
	m_rm.periodicMaintenance();
    }
    

    public boolean checkPassed() {
	Iterator it;
	Id key;
	boolean passed = true;

	//System.out.println("keys.size()= " + m_keys.numElements() + " refreshedKeys.size()= " + m_refreshedKeys.numElements());
	// Here we compare the sets m_keys & m_refreshedKeys
	
	// Testing for missing objects
	it = m_refreshedKeys.getIterator();
	while(it.hasNext()) {
	    key = (Id) it.next();
	    if(!m_keys.isMember(key)) {
		passed = false;
		System.out.println(" At " + getNodeId() + " object " + key + " is Missing");
	    }
	}

	// Testing for stale objects
	it = m_keys.getIterator();
	while(it.hasNext()) {
	    key = (Id) it.next();
	    if(!m_refreshedKeys.isMember(key)) {
		passed = false;
		System.out.println(" At " + getNodeId() + " object " + key + " is Stale");
	    }
	}
	return passed;

    }

    public void clearRefreshedKeys() {
	m_refreshedKeys = new IdSet();

    }

    public PastryNode getPastryNode() {
	return thePastryNode;
    }

    


}








