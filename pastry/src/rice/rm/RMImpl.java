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


package rice.rm;

import rice.pastry.*;
import rice.pastry.direct.*;
import rice.pastry.standard.*;
import rice.pastry.join.*;
import rice.pastry.client.*;
import rice.pastry.security.*;
import rice.pastry.messaging.*;
import rice.pastry.routing.*;
import rice.pastry.leafset.*;

import java.lang.*;
import java.util.Vector;
import java.util.Hashtable;
import java.util.Enumeration;

import rice.rm.messaging.*;
import rice.rm.testing.*;


/**
 * @(#) RMImpl.java
 *
 * This (Replica Manager Module) implements the RM interface. 
 * This runs as a Application which dynamically (in the presence of nodes
 * joining and leaving the network) maintains the invariant
 * that objects are replicated over the requested number of replicas.
 *
 * @version $Id$
 * @author Animesh Nandi
 */


public class RMImpl extends CommonAPIAppl implements RM {


    /**
     * The address associated with RM. Used by lower pastry modules to 
     * demultiplex messages having this address to this RM application.
     */
    private static RMAddress _address = new RMAddress();


    /**
     * Credentials for this application.
     */
    private Credentials _credentials;

    /**
     * SendOptions to be used on pastry messages.
     */
    public SendOptions _sendOptions;

    private boolean m_ready;

    public int m_seqno;

    public IdRange myRange;

    public int rFactor; // standard rFactor to be used

    public RMClient app = null; // Application that uses this Replica Manager

    
    // This is set to the application if the underlying pastry node
    // was unready when the application registered.
    public RMClient m_unreadyApp = null;


    public Hashtable m_pendingObjects;


    private static final int NSMAXSIZE = 24;

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

    private static class RMAddress implements Address {
	private int myCode = 0x8bed147c;
	
	public int hashCode() { return myCode; }

	public boolean equals(Object obj) {
	    return (obj instanceof RMAddress);
	}
    }


    /**
     * Constructor : Builds a new ReplicaManager(RM) associated with this 
     * pastryNode.
     * @param pn the PastryNode associated with this application
     * @return void
     */
    public RMImpl(PastryNode pn)
    {
	super(pn);
	m_ready = pn.isReady();
	_credentials = new PermissiveCredentials();
	_sendOptions = new SendOptions();
	m_seqno = 0;
	m_pendingObjects = new Hashtable();
    }

    /** 
     * Returns true if the RM substrate is ready. The RM substrate is
     * ready when underlying PastryNode is ready.
     */
    public boolean isReady() {
	return m_ready;
    }

    /* Gets the local NodeHandle associated with this Scribe node.
     *
     * @return local handle of Scribe node.
     */
    public NodeHandle getLocalHandle() {
	return thePastryNode.getLocalHandle();
    }

    public PastryNode getPastryNode() {
	return thePastryNode;
    }


    /**
     * Called by pastry when a message arrives for this application.
     * @param msg the message that is arriving.
     * @return void
     */
    //public void messageForAppl(Message msg){
    public void deliver(Id key, Message msg) {
	RMMessage  rmmsg = (RMMessage)msg;
	rmmsg.handleDeliverMessage( this);
	
    }

  
    /**
     * Registers the application to the RM.
     * @param _rFactor the replication factor 
     * @param _app the application, which is an instance of RMClient
     */
    public boolean register(RMClient _app, int _rFactor) {
	//System.out.println("register called on " + getNodeId());
	app = _app;
	rFactor = _rFactor;
	if(isReady()) {
	    app.rmIsReady();

	    myRange = range(getLocalHandle(), rFactor, getNodeId(), true);
	    //System.out.println("MyRange= " + myRange);
	    //System.out.println("Need to do initial fetching of keys from " + getNodeId());
	     
	    if(!DirectRMRegrTest.setupDone)
		return true;


	    IdRange requestRange = myRange;
	    
	    Vector rangeSet = new Vector();
	    rangeSet.add(requestRange);
	    NodeSet set = requestorSet(rangeSet);
	    for(int i=0; i<set.size(); i++) {
	       
		NodeHandle toNode;
		RMRequestKeysMsg msg;
		
		toNode = set.get(i);
		if(toNode.getNodeId().equals(getNodeId()))
		   continue;
		msg = new RMRequestKeysMsg(getLocalHandle(),getAddress(), getCredentials(), m_seqno ++, rangeSet, false);
		//System.out.println(getNodeId() + "sending RequestKeys msg to " + toNode.getNodeId());
		route(null, msg, toNode);
	    }


	}
	else
	    m_unreadyApp = _app;
	return true;
    }

     /**
     * This is called when the underlying pastry node is ready.
     */
    public void notifyReady() {
	// Temporary bug-fix
	while(simulate());

	//System.out.println("notifyReady called for RM application on" + getNodeId()); 
	m_ready = true;

	if(app!=null) {
	    if(m_unreadyApp != null) {
		m_unreadyApp.rmIsReady();
	    }

	    myRange = range(getLocalHandle(), rFactor, getNodeId(), true);
	    //System.out.println("MyRange= " + myRange);
	    //System.out.println("Need to do initial fetching of keys from " + getNodeId());

	    if(!DirectRMRegrTest.setupDone)
		return;


	    IdRange requestRange = myRange;
	    
	    Vector rangeSet = new Vector();
	    rangeSet.add(requestRange);
	    NodeSet set = requestorSet(rangeSet);
	    for(int i=0; i<set.size(); i++) {
	       
		NodeHandle toNode;
		RMRequestKeysMsg msg;
		
		toNode = set.get(i);
		if(toNode.getNodeId().equals(getNodeId()))
		   continue;
		msg = new RMRequestKeysMsg(getLocalHandle(),getAddress(), getCredentials(), m_seqno ++, rangeSet, false);
		//System.out.println(getNodeId() + "sending RequestKeys msg to " + toNode.getNodeId());
		route(null, msg, toNode);
	    }

	}
    }

    

    /**
     * Called by the application when it needs to replicate an object into
     * k nodes closest to the object key.
     *
     * @param objectKey  the pastry key for the object
     * @param object the object
     * @return true if operation successful else false
     */
    public boolean replicate(Id objectKey, Object object) {
	m_pendingObjects.put(objectKey, new ReplicateEntry(object));
	RMReplicateMsg msg = new RMReplicateMsg(getLocalHandle(),getAddress(),objectKey, getCredentials(), m_seqno ++);
	route(objectKey, msg, null);
	return true;

    }


    
    /**
     * Returns the address of this application.
     * @return the address.
     */
    public Address getAddress() {
	return _address;
    }

    /**
     * Returns the credentials for the application
     * @return the credentials
     */
    public Credentials getCredentials() {
	return this._credentials;
    }


    
    /**
     * Implements the main algorithm for keeping the invariant that an object 
     * would  be stored in k closest nodes to the objectKey  while the nodes are
     * coming up or going down. 
     * @param nh NodeHandle of the node which caused the leafSet change
     * @param wasAdded true if added, false if removed
     * @return void
     */
    public void update(NodeHandle nh, boolean wasAdded) {
	if(!isReady() || (app == null))
	    return;

	//System.out.println("leafsetChange(" + nh.getNodeId() + " , " + wasAdded + ")" + " at " + getNodeId());

	IdRange prev_Range;
	if(myRange !=null) {

	    prev_Range = new IdRange(myRange);
	}
	else {
	    prev_Range = null;
	}
	

	myRange = range(getLocalHandle(), rFactor, getNodeId(), true);


	if(!DirectRMRegrTest.setupDone)
	    return;

	
	if(myRange.equals(prev_Range))
	    return;

	
	if(wasAdded) {
	    // A new node was added
	    // No fetching of keys required
	    // The upcall isResponsible(IdRange) in RMClient
	    // enables the RMClient to get rid of the keys it is not 
	    // responsible for

	    app.isResponsible(myRange);
	    
	}
	else {

	    // We need not call app.isResponsible() since here the range
	    // increases strictly.
	    
	    // This means that we now become responsible for an extra bit of 
	    // range. Which means we have to fetch keys.
	    
	    // Now we need to take the diff of the two ranges 
	    // (prev_Range, myRange) as use that as the range of additional 
	    // keys to fetch

	    
	    IdRange requestRange1 ;
	    IdRange requestRange2 ;
	    Vector rangeSet = new Vector();
	    if(prev_Range == null) {
		requestRange1 = myRange;
		rangeSet.add(requestRange1);
	    }
	    else {
		// Compute the diff of the two ranges
		//System.out.println("checking subtract calculation");
		//System.out.println("MyRange= " + myRange);
		//System.out.println("PrevRange= " + prev_Range);
		requestRange1 = myRange.subtract(prev_Range, true);
		//System.out.println("request Range1 " + requestRange1);
		requestRange2 = myRange.subtract(prev_Range, false);
		//System.out.println("request Range2 " + requestRange2);
		if(!requestRange1.isEmpty())
		    rangeSet.add(requestRange1);
		if(!requestRange2.isEmpty())
		    rangeSet.add(requestRange2);

	    }
	    
	    NodeSet set = requestorSet(rangeSet);
	    //System.out.println("requestorset= " + set);
	    for(int i=0; i<set.size(); i++) {
		NodeHandle toNode;
		RMRequestKeysMsg msg;
		
		toNode = set.get(i);
		if(toNode.getNodeId().equals(getNodeId()))
		   continue;
		//System.out.println(getNodeId() + " sending requestkeys msg to" + toNode.getNodeId());
		msg = new RMRequestKeysMsg(getLocalHandle(),getAddress(), getCredentials(), m_seqno ++, rangeSet, false);
		
		route(null, msg, toNode);
	    }

	}
	

    }


    public RMImpl.ReplicateEntry getPendingObject(Id key) {
	return (RMImpl.ReplicateEntry)m_pendingObjects.get(key);
    }

    public void removePendingObject(Id key) {
	m_pendingObjects.remove(key);
    }



  

    /**
     * This function determines the nodes to which the local node requests
     * for keys
     * @param rangeSet - contains a list of IdRanges that this node will request for 
     */
    private NodeSet requestorSet(Vector rangeSet)
    {
	NodeSet requestors = new NodeSet();
	for(int i=0; i<rangeSet.size(); i++) {
	    IdRange range;
	    range = (IdRange)rangeSet.elementAt(i);
	    if(!range.isEmpty()) {
		Id ccw, cw;
		NodeSet set;
		ccw = range.getCCW();
		cw = range.getCW();
		set = replicaSet(ccw, rFactor + 1);
		for(int j=0; j < set.size(); j++) {
		    NodeHandle nh;
		    nh = set.get(j);
		    requestors.put(nh);
		}
		set = replicaSet(cw, rFactor + 1);
		for(int j=0; j < set.size(); j++) {
		    NodeHandle nh;
		    nh = set.get(j);
		    requestors.put(nh);
		}
	    }

	}
	return requestors;
    }
    



    private boolean simulate() { 
	return DirectRMRegrTest.simulator.simulate(); 
    }

}










