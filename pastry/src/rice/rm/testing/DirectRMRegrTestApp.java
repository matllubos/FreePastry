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
import rice.pastry.client.*;
import rice.pastry.messaging.*;

import rice.pastry.security.*;
import rice.rm.*;
import rice.rm.messaging.*;
import rice.pastry.direct.*;

import java.util.*;
import java.io.*;

/**
 * @(#) DirectRMRegrTestApp.java
 *
 * An application used by the DirectRMRegrTest suite for RM.
 *
 * @version $Id$
 *
 * @author Animesh Nandi 
 */

public class DirectRMRegrTestApp extends PastryAppl implements RMClient
{

    private PastryNode m_pastryNode ;
    private Credentials m_credentials;
    public RM m_rm;
    public int m_appCount;
    public Hashtable m_objects;

    // checkpassed is used to check the system's invariants with regard to 
    // position of replicas. It can be set to false either in refresh() or check() method
    public boolean checkpassed = true;

    /**
     * The receiver address for the RMClient system.
     */
    protected static Address m_address = new DirectRMRegrTestAppAddress();

    private static class DirectRMRegrTestAppAddress implements Address {
	private int myCode = 0x6bec747c;
	
	public int hashCode() { return myCode; }

	public boolean equals(Object obj) {
	    return (obj instanceof DirectRMRegrTestAppAddress);
	}
    }


     private class ObjectState {
	 private int refreshCount;
	 
	 public ObjectState(int k){
	     refreshCount = k;
	 }
	 public int getrefreshCount(){
	     return refreshCount;
	 }
	 
	 public void setrefreshCount(int value) {
	     refreshCount = value;
	 }
     }

    /**
     * Constructor
     * @param node The local PastryNode
     * @param rm The underlying replica manager 
     * @param cred The credentials 
     */
    public DirectRMRegrTestApp( PastryNode pn, RM rm, Credentials cred) {
	super(pn);
	m_rm = rm;
	m_credentials = cred;
	if(cred == null) {
	     m_credentials = new PermissiveCredentials();
	}
	m_pastryNode = pn;
	m_objects = new Hashtable();
	m_rm.register(m_address,this);
    }


    public void rmIsReady() {

    }

    // Upcall from replica manager
    public void responsible(NodeId objectKey, Object object) {
	//System.out.println("responsible() called on node" + getNodeId());
	if(m_objects.containsKey(objectKey)) {
	    // object exists already, so this represents a superfluous message
	    System.out.println("WARNING: responsible() called on " + getNodeId() + " for object " + objectKey + " that aleady existed");
	}
	else {
	    // we add this object to our objects hashtable, and set its refresh count
	    // as 1. 
	    m_objects.put(objectKey, new ObjectState(1));

	}
    }

    // Upcall from replica manager
    public void notresponsible(NodeId objectKey) {
	//System.out.println("notresponsible() called on node" + getNodeId());
	if(!m_objects.containsKey(objectKey)) {
	    // object does not exist, so this represents a superfluous message
	    System.out.println("WARNING: notresponsible() called on " + getNodeId() + " for object " + objectKey + " that did not exist");
	}
	else {
	    // we remove this object from our objects hashtable
	    m_objects.remove(objectKey);

	}
    }
    

    // Upcall from replica manager
    public void refresh(NodeId objectKey) {
	//System.out.println("refresh() called on node" + getNodeId());
	if(!m_objects.containsKey(objectKey)) {
	    // object does not exists, so this represents a error 
	    System.out.println("ERROR: refresh() called on " + getNodeId() + " for object " + objectKey + " that did not exist");
	    checkpassed = false;
	}
	else {
	    // we reset the refreshCount of the object
	    ObjectState state;
	    state = (ObjectState)m_objects.get(objectKey);
	    state.setrefreshCount(0);

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

    
    // abstract methods, to be overridden by the derived application object
    public  Address getAddress() {
	return m_address;
    }

    public Credentials getCredentials() {
	return m_credentials;
    }


    public  void messageForAppl(Message msg) {

    }

    // This function will be invoked to check if the refreshCount of all the objects
    public boolean check() {
	Enumeration keys = m_objects.keys();	
	ObjectState state;
	NodeId objectKey;

	while(keys.hasMoreElements()){
	    objectKey = (NodeId)keys.nextElement();
	    state = (ObjectState)m_objects.get(objectKey);
	    if(state.getrefreshCount() != 0) {
		System.out.println("ERROR: Node " + getNodeId() + " holds object " + objectKey + " when it should not");
		checkpassed = false;
	    }

	}
	return checkpassed;

    }

}








