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
import rice.pastry.dist.*;
import rice.pastry.direct.*;
import rice.pastry.standard.*;
import rice.pastry.join.*;
import rice.pastry.client.*;
import rice.pastry.security.*;
import rice.pastry.messaging.*;
import rice.pastry.routing.*;
import rice.pastry.leafset.*;
import rice.pastry.routing.*;

import java.lang.*;
import java.util.*;


import rice.rm.messaging.*;
import rice.rm.testing.*;


/**
 * @(#) RMImpl.java
 *
 * This (Replica Manager Module) implements the RM interface. 
 * This runs as a Application which dynamically (in the presence of nodes
 * joining and leaving the network) maintains the invariant
 * that objects are replicated over the requested number of replicas.
 * This module has been built over the CommonAPI defined in
 * "Towards a Common API for Structured Peer-to-Peer Overlays." Frank
 * Dabek, Ben Zhao, Peter Druschel, John Kubiatowicz and Ion
 * Stoica. In Proceedings of the 2nd International Workshop on
 * Peer-to-peer Systems (IPTPS'03) , Berkeley, CA, February 2003.
 * Hence the implementation is meant to be seamlessly portable to the
 * other existing P2P protocols.
 *
 * @version $Id$
 *
 * @author Animesh Nandi
 */


public class RMImpl extends CommonAPIAppl implements RM {

    /**
     * The Credentials object to be used for all messaging through Pastry.
     */
    private Credentials _credentials;

    /**
     * The SendOptions object to be used for all messaging through Pastry.
     */
    public SendOptions _sendOptions;

    /**
     * This flag is set to true when this RM substrate is ready. The
     * RM substrate is ready when the underlying Pastry node is ready.
     */
    private boolean m_ready;

    /**
     * This will be incremented for every message that this node sends
     * remotely. Specifically, incremented for every RMRequestKeysMsg,
     * RMResponseKeysMsg it sends. This is to aid in the debugging phase.
     */
    public int m_seqno;

    /**
     * This is to keep track of events with respect to RMRequestKeysMsg only.
     * Since RMRequestKeysMsg is the only message that needs a timeout
     * mechanism. Incremented for every different RMRequestKeys message
     * generation event. That is if a Timeout occurred for a RMRequestKeysMsg,
     * then the subsequent RMRequestKeysMsg, that will be resent will have the
     * SAME eventId but an increased value of seqNo.
     */
    public int m_eId;

    /**
     * This represents the range of object identifiers in the Id space for
     * which this node (by virtue of its position in the Id Space relative
     * to the positions of the other Pastry nodes) is an i-root (0<=i<=k). 
     */
    public IdRange myRange;

    /**
     * rFactor stands for the number of additional replicas for an object.
     * The primary replica is denoted a 0-root, other replicas are
     * denoted as i-root (1<=i<=rFactor).
     */
    public int rFactor; 

    /**
     * Application that uses this Replica Manager.
     */
    public RMClient app; 

    /**
     * This hashtable is keyed by the NodeId of the node to whom
     * this local node is requesting for a set of keys in the Keys
     * Exchange protocol. Since potentially the number of keys in 
     * a particular range could be very high, asking for the entire
     * set of keys in the intial range could result in huge packet 
     * sizes. In order to circumvent this problem, we devise some
     * strategies in the Keys Exchange protocol to split this range
     * into smaller ranges and issue requests for these subranges
     * one at a time. This requires state in the form of this hashtable
     * to maintain the list f pending ranges, for which a request for
     * the key set is yet to be issued.
     */  
    public Hashtable m_pendingRanges;

    /**
     * This table will be used by the Timeout mechanism in 
     * RMRequestKeysMsg.
     */
    public Hashtable m_pendingEvents;
    
    /**
     * This value represents the maximum size of the keySet corresponding
     * to a requested id range that we would like to fit in a single message.
     * As a result of this, we need some strategies to split ranges in the 
     * Keys Exchange Protocol. 
     */  
    public static int MAXKEYSINRANGE = 1024;

    /**
     * This value represents the splitting factor by which a range is 
     * split into in the Keys Exchange Protocol. The splitting method
     * is recursive binary splitting until we split the range into 
     * a total of SPLITFACTOR parts.
     */
    public static int SPLITFACTOR = 16;
    
    /**
     * This is the per entry state corresponding to the m_pendingRanges
     * hashtable that we maintain in the Keys Exchange Protocol.
     */
    public static class KEPenEntry {
	private IdRange reqRange;
	private int numKeys;

	
	public KEPenEntry(IdRange _reqRange) {
	    reqRange = _reqRange;
	    numKeys = -1;
	}
    
	public KEPenEntry(IdRange _reqRange, int _numKeys) {
	    reqRange = _reqRange;
	    numKeys = _numKeys;
	}

	public IdRange getReqRange() {
	    return reqRange;
	}

	public int getNumKeys() {
	    return numKeys;
	}

	public void updateNumKeys(int val) {
	    numKeys = val;
	}

	/**
	 * Equality is based only on the reqRange part, does not care
	 * about the numKeys argument.
	 */
	public boolean equals(Object obj) {
	    KEPenEntry oEntry;
	    oEntry = (KEPenEntry)obj;
	    if (reqRange.equals(oEntry.getReqRange())) 
		return true;
	    else 
		return false;
	}


	public String toString() {
	  String s = "PE(";
	  s = s + reqRange + numKeys;
	  s = s + ")";
	  return s;

	}


    }



    /**
     * Builds a new ReplicaManager(RM) associated with a particular RMclient.
     * @param pn the PastryNode associated with this application
     * @param _app the client associated with this replica manager
     * @param _rFactor the replicaFactor associated with the replica manager
     * @param instance the string used to sucessfully instantiate different
     *                 application instances on the same pastry node
     */
    public RMImpl(PastryNode pn, RMClient _app, int _rFactor, String instance)
    {
	super(pn, instance);
	app = _app;
	m_ready = pn.isReady();
	rFactor = _rFactor;

	_credentials = new PermissiveCredentials();
	_sendOptions = new SendOptions();
	m_seqno = 0;
	m_eId = 0;
	m_pendingRanges = new Hashtable();
	m_pendingEvents = new Hashtable();
	if(isReady()) {

	    
	    myRange = range(getLocalHandle(), rFactor, getNodeId(), true);
	    app.rmIsReady(this);
	    //System.out.println("MyRange= " + myRange);
	    //System.out.println(" Constructor::Need to do initial fetching of keys from " + getNodeId());
	    
	    IdRange requestRange = myRange;
	    
	    Vector rangeSet = new Vector();
	    if((requestRange!=null) && !requestRange.isEmpty())
		rangeSet.add(new RMMessage.KEEntry(requestRange,true));
	    
	    NodeSet set = requestorSet(rangeSet);
	    
	    sendKeyRequestMessages(set, rangeSet);

	    // We trigger the periodic Maintenance protocol
	    if(getPastryNode() instanceof DistPastryNode) {
		RMMaintenanceMsg msg;
		msg  = new RMMaintenanceMsg(getNodeHandle(), getAddress(), getCredentials(), m_seqno ++); 
		getPastryNode().scheduleMsgAtFixedRate(msg, RMMaintenanceMsg.maintStart * 1000, RMMaintenanceMsg.maintFreq * 1000);

	    }

	}
    }


    /** 
     * Returns true if the RM substrate is ready. The RM substrate is
     * ready when underlying PastryNode is ready.
     */
    public boolean isReady() {
	return m_ready;
    }

    /**
     * Gets the local NodeHandle associated with this Pastry node.
     * @return local handle of the underlying pastry node.
     */
    public NodeHandle getLocalHandle() {
	return thePastryNode.getLocalHandle();
    }

    /**
     * Gets the underlying local Pastry node.
     * @return local pastry node.
     */
    public PastryNode getPastryNode() {
	return thePastryNode;
    }




    /**
     * Used to insert entries to the m_pendingEvents hashtable
     * @param toNode the node with whom the local node is communicating
     *               this is the key for this entry in the hashtable
     * @param eId the event Id associated with this RMRequestKeys msg.
     */
    public void addPendingEvent(NodeId toNode, int eId) {
	//System.out.println("At " + getNodeId() + "addPendingEvent( " + toNode + " , " + eId + " ) ");
	Integer entry = new Integer(eId); 
	Vector setOfEvents;
	if(m_pendingEvents.containsKey(toNode)) {
	    setOfEvents = (Vector) m_pendingEvents.get(toNode);
	    if(!setOfEvents.contains(entry))
		setOfEvents.add(entry);
	}
	else {
	    setOfEvents = new Vector();
	    setOfEvents.add(entry);
	    m_pendingEvents.put(toNode, setOfEvents);
	}
	
    }



    /**
     * Used to remove entries to the m_pendingEvents hashtable
     * @param toNode the node with whom the local node is communicating
     *               this is the key for this entry in the hashtable
     * @param eId the event Id associated with this RMRequestKeys msg.
     */
    public void removePendingEvent(NodeId toNode, int eId) {
	//System.out.println("At " + getNodeId() + "removePendingEvent( " + toNode + " , " + eId + " ) ");

	Vector setOfEvents;
	if(m_pendingEvents.containsKey(toNode)) {
	    setOfEvents = (Vector) m_pendingEvents.get(toNode);
	    if(setOfEvents.contains(new Integer(eId))) {
		setOfEvents.remove(new Integer(eId));
		if(setOfEvents.isEmpty())
		    m_pendingEvents.remove(toNode);
	    }
	    else {
		// Possible cause is message duplication, or Timeout expiring while the response msg
		// is int transit
		//System.out.println("At " + getNodeId() + "Warning1: In removePendingEvent(" + toNode + "," + eId +  " ): Should not happen");
	    }
	}
	else {
	    // Possible cause is message duplication,  or Timeout expiring while the response msg
		// is int transit
		//System.out.println("At " + getNodeId() + "Warning2: In removePendingEvent(" + toNode + "," + eId +  " ): Should not happen");
	    
	} 

    }



    /**
     * Used to check for existence of an entry in the m_pendingEvents hashtable
     * @param toNode the node with whom the local node is communicating
     *               this is the key for this entry in the hashtable
     * @param eId the event Id associated with this RMRequestKeys msg.
     */
    public boolean isPendingEvent(NodeId toNode, int eId) {
	//System.out.println("At " + getNodeId() + "isPendingEvent( " + toNode + " , " + eId + " ) ");

	Vector setOfEvents;
	if(!m_pendingEvents.containsKey(toNode))
	    return false;
	
	setOfEvents = (Vector) m_pendingEvents.get(toNode);
	if(setOfEvents.contains(new Integer(eId))) 
	    return true;
	else
	    return false;
    }
   


    /**
     * Used to insert a pending range to the m_pendingRanges hashtable
     * @param toNode the node with whom the local node is communicating
     *               this is the key for this entry in the hashtable
     * @param reqRange the pending range.
     */
    public void addPendingRange(NodeId toNode, IdRange reqRange) {
	//System.out.println("At " + getNodeId() + "addPendingRange( " + toNode + " , " + reqRange + " ) ");
	RMImpl.KEPenEntry entry= new RMImpl.KEPenEntry(reqRange); 
	Vector setOfRanges;
	if(m_pendingRanges.containsKey(toNode)) {
	    setOfRanges = (Vector) m_pendingRanges.get(toNode);
	    if(!setOfRanges.contains(entry))
		setOfRanges.add(entry);
	}
	else {
	    setOfRanges = new Vector();
	    setOfRanges.add(entry);
	    m_pendingRanges.put(toNode, setOfRanges);
	}
	
    }


    /**
     * Used to update the state of a pending range to the m_pendingRanges
     * hashtable.
     * @param toNode the node with whom the local node is communicating
     *               this is the key for this entry in the hashtable
     * @param reqRange the pending range.
     * @param numKeys the expected number of keys associated with this subrange
     */
     public void updatePendingRange(NodeId toNode, IdRange reqRange, int numKeys) {
	 //System.out.println("At " + getNodeId() + "updatePendingRange( " + toNode + " , " + reqRange + " , " + numKeys + " ) ");
	RMImpl.KEPenEntry entry= new RMImpl.KEPenEntry(reqRange); 
	Vector setOfRanges;
	if(m_pendingRanges.containsKey(toNode)) {
	    setOfRanges = (Vector) m_pendingRanges.get(toNode);
	    if(setOfRanges.contains(entry)) {
		int index; 
		index = setOfRanges.indexOf(entry);
		// Note that the actual entry is a different entry
		// because of the way we defined the equals() method on
		// KEPenEntry
		RMImpl.KEPenEntry actualEntry;
		actualEntry = (RMImpl.KEPenEntry) setOfRanges.elementAt(index);
		actualEntry.updateNumKeys(numKeys);
	    }
	    else {
		// Possible cause is Message Duplication
		//System.out.println("At " + getNodeId() + "Warning1: In updatePendingRange(" + toNode + "," + reqRange + " , " + numKeys + " ): Should not happen");

	    }
	}
	else {
	    // Possible cause is Message Duplication
	    //System.out.println("At " + getNodeId() + "Warning2: In updatePendingRange(" + toNode + "," + reqRange + " , " + numKeys + " ): Should not happen");
	}
	
    }



    /**
     * Used to remove a pending range to the m_pendingRanges hashtable
     * @param toNode the node with whom the local node is communicating
     *               this is the key for this entry in the hashtable
     * @param reqRange the pending range.
     */
    public void removePendingRange(NodeId toNode, IdRange reqRange) {
	//System.out.println("At " + getNodeId() + "removePendingRange( " + toNode + " , " + reqRange + " ) ");

	Vector setOfRanges;
	if(m_pendingRanges.containsKey(toNode)) {
	    setOfRanges = (Vector) m_pendingRanges.get(toNode);
	    if(setOfRanges.contains(new RMImpl.KEPenEntry(reqRange))) {
		setOfRanges.remove(new RMImpl.KEPenEntry(reqRange));
		if(setOfRanges.isEmpty())
		    m_pendingRanges.remove(toNode);
	    }
	    else {
		// Possible cause is message duplication
		//System.out.println("At " + getNodeId() + "Warning1: In removePendingRange(" + toNode + "," + reqRange +  " ): Should not happen");
	    }
	}
	else {
	    // Possible cause is message duplication
	    //System.out.println("At " + getNodeId() + "Warning2: In removePendingRange(" + toNode + "," + reqRange +  " ): Should not happen");
	    
	} 

    }

    


    /**
     * Iterates over the list of pending Ranges and splits the ranges
     * if the expected number of keys in a range is greater than 
     * MAXKEYSINRANGE.
     * @param toNode the node with which this local was communicating in
     *               the keys exchange protocol. 
     */
    public void splitPendingRanges(NodeId toNode) {
	//System.out.println("splitRanges( " + toNode + " )");

	Vector setOfRanges;
	if(m_pendingRanges.containsKey(toNode)) {
	    setOfRanges = (Vector) m_pendingRanges.get(toNode);
	    for(int i=0; i< setOfRanges.size(); i++) {
		RMImpl.KEPenEntry entry;
		entry = (RMImpl.KEPenEntry)setOfRanges.elementAt(i);
		if(entry.getNumKeys() > MAXKEYSINRANGE) {
		    setOfRanges.remove(i);
		    // We split this range
		    Vector allParts = splitRange(entry.getReqRange());
		    for(int j=0; j < allParts.size(); j++) {
			IdRange part;
			RMImpl.KEPenEntry newEntry;
			part = (IdRange)allParts.elementAt(j);
			newEntry = new RMImpl.KEPenEntry(part);
			setOfRanges.insertElementAt(newEntry, i+j);

		    }

		} 
	    }
	}
	else {
	    System.out.println("Warning2:: In splitRanges() : Should not happen");
	}
    }

    /**
     * Returns a Vector of the split parts of this range. The splitting
     * method is recursive binary spliting until we get a total SPLITFACTOR
     * number of subranges from the intitial range.
     * @param bigRange the intial range that needs to be split
     * @return the list of subranges that were got by splitting 'bigRange'.
     */
    private Vector splitRange(IdRange bigRange) {
	Vector parts = new Vector();
	parts.add(bigRange);
	while(parts.size() < SPLITFACTOR ) {
	    IdRange range;
	    IdRange ccwHalf;
	    IdRange cwHalf;
	    range = (IdRange)parts.elementAt(0);
	    ccwHalf = range.ccwHalf();
	    cwHalf = range.cwHalf();
	    parts.add(ccwHalf);
	    parts.add(cwHalf);
	    parts.remove(0);
	}
	return parts;
    }


    /**
     * Gets the list of pending Ranges corresponding to a node that
     * we are communicating to in the Keys Exchange Protocol. 
     * @param toNode the node with which this local was communicating in
     *               the keys exchange protocol. 
     */    
    public Vector getPendingRanges(NodeId toNode) {
	Vector setOfRanges;
	if(m_pendingRanges.containsKey(toNode)) {
	    setOfRanges = (Vector) m_pendingRanges.get(toNode);
	    return setOfRanges; 
	}
	else { 
	    System.out.println("Warning: getPendingRanges()");
	    return new Vector();
	}

    }


    /**
     * Prints the list of pending Ranges corresponding to a node that
     * we are communicating to in the Keys Exchange Protocol. 
     * @param toNode the node with which this local was communicating in
     *               the keys exchange protocol. 
     */ 
    public void printPendingRanges(NodeId toNode) {
	Vector setOfRanges;
	if(m_pendingRanges.containsKey(toNode)) {
	    setOfRanges = (Vector) m_pendingRanges.get(toNode);
	    //System.out.print("At " + getNodeId() + " Pending Ranges= ");
	    for(int i=0; i< setOfRanges.size(); i++) {
		RMImpl.KEPenEntry entry;
		entry = (RMImpl.KEPenEntry)setOfRanges.elementAt(i);
		System.out.print(entry + " , ");
	    }
	    System.out.println("");
	}
	else 
	    System.out.println("Warning: printPendingRanges()");

    }



    /**
     * Called by pastry when a message arrives for this application.
     * @param msg the message that is arriving.
     */
    public void deliver(Id key, Message msg) {
	RMMessage  rmmsg = (RMMessage)msg;
	rmmsg.handleDeliverMessage( this);
	
    }

  
    /**
     * This is called when the underlying pastry node is ready.
     */
    public void notifyReady() {
	if(app!=null) {
	    //System.out.println("notifyReady called for RM application on" + getNodeId()); 
	    m_ready = true;
	    myRange = range(getLocalHandle(), rFactor, getNodeId(), true);
	    app.rmIsReady(this);
	    //System.out.println("MyRange= " + myRange);
	    //System.out.println("notifyReady()::Need to do initial fetching of keys from " + getNodeId());
	    
	    IdRange requestRange = myRange;
	
	    Vector rangeSet = new Vector();
	    if((requestRange!=null) && !requestRange.isEmpty())
		rangeSet.add(new RMMessage.KEEntry(requestRange,true));
	
	    NodeSet set = requestorSet(rangeSet);
	    sendKeyRequestMessages(set, rangeSet);


	    // We trigger the periodic Maintenance protocol
	    if(getPastryNode() instanceof DistPastryNode) {
		RMMaintenanceMsg msg;
		msg  = new RMMaintenanceMsg(getNodeHandle(), getAddress(), getCredentials(), m_seqno ++); 
		getPastryNode().scheduleMsgAtFixedRate(msg, RMMaintenanceMsg.maintStart * 1000, RMMaintenanceMsg.maintFreq * 1000);

	    }

	}

    }

    
    /**
     * This is the periodic maintenance protocol which removes stale objects
     * as well as checks to see if there is any missing object. This call
     * is invoked by the RMMaintenanceMsg message that is scheduled to be 
     * periodically invoked at the local node.
     */
    public void periodicMaintenance() {
	// Remove stale objects
	if(myRange!=null)
	    app.isResponsible(myRange);
	
	
	// Fetch missing objects
	IdRange requestRange = myRange;
	
	Vector rangeSet = new Vector();
	if((requestRange!=null) && !requestRange.isEmpty())
	    rangeSet.add(new RMMessage.KEEntry(requestRange,true));
	NodeSet set = requestorSet(rangeSet);
	sendKeyRequestMessages(set, rangeSet);

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
     * would  be stored in k closest nodes to the objectKey  while the nodes 
     * are coming up or going down. 
     * @param nh NodeHandle of the node which caused the neighborSet change
     * @param wasAdded true if added, false if removed
     */
    public void update(NodeHandle nh, boolean wasAdded) {
	if(!isReady())
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


	
	if((myRange== null) || (prev_Range == null) || myRange.equals(prev_Range))
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
		rangeSet.add(new RMMessage.KEEntry(requestRange1,true));
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
		if(requestRange2.equals(requestRange1))
		    requestRange2 = new IdRange();
		if(!requestRange1.isEmpty())
		    rangeSet.add(new RMMessage.KEEntry(requestRange1, true));
		if(!requestRange2.isEmpty())
		    rangeSet.add(new RMMessage.KEEntry(requestRange2, true));

	    }
	    
	    NodeSet set = requestorSet(rangeSet);

	    sendKeyRequestMessages(set, rangeSet);

	}
	
	
    }


    /**
     * This function determines the nodes to which the local node requests
     * for keys.
     * @param rangeSet - contains a list of IdRanges that this node will
     *                   request for. 
     */
    private NodeSet requestorSet(Vector rangeSet)
    {
	NodeSet requestors = new NodeSet();
	for(int i=0; i<rangeSet.size(); i++) {
	    IdRange range;
	    range = ((RMMessage.KEEntry)rangeSet.elementAt(i)).getReqRange();
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


    /**
     * We send RMRequestKeys messages to the the nodes in the 'set'
     * asking for keys in the ranges specified in the 'rangeSet'. 
     * Additionally, in order to implement the TIMEOUT mechanism to
     * handle loss of RMRequestKeysMsg, we wrap the RMRequestKeysMsg
     * in a RMTimeoutMsg which we schedule on the local node after a 
     * TIMEOUT period.
     */
    public void sendKeyRequestMessages(NodeSet set, Vector rangeSet) {
	if(rangeSet.size() == 0)
	    return;
	for(int i=0; i<set.size(); i++) {
	    
	    NodeHandle toNode;
	    RMRequestKeysMsg msg;
	    
	    toNode = set.get(i);
	    if(toNode.getNodeId().equals(getNodeId()))
		continue;
	    for(int j=0; j< rangeSet.size(); j++) {
		RMMessage.KEEntry entry = (RMMessage.KEEntry) rangeSet.elementAt(j);
		IdRange reqRange = entry.getReqRange();
		addPendingRange(toNode.getNodeId(),reqRange);

	    }
	    int eId = m_eId ++;
	    msg = new RMRequestKeysMsg(getLocalHandle(),getAddress(), getCredentials(), m_seqno ++, rangeSet, eId);

	    if(getPastryNode() instanceof DistPastryNode) {
		// We will also wrap this message in order to implement the TIMEOUT mechanism.
		RMRequestKeysMsg.WrappedMsg wmsg = new RMRequestKeysMsg.WrappedMsg(msg, toNode);
		RMTimeoutMsg tmsg = new RMTimeoutMsg(getNodeHandle(), getAddress(), getCredentials(), m_seqno ++, wmsg);
		getPastryNode().scheduleMsg(tmsg, RMRequestKeysMsg.TIMEOUT * 1000);
		addPendingEvent(toNode.getNodeId(), eId); 
	    }

	    //System.out.println("At " + getNodeId() + "sending RequestKeys msg to " + toNode.getNodeId());
	    route(null, msg, toNode);
	}
    }


     /**
     * Called by client(RMClient) to notify the RM substrate of the
     * presence of a key corresponding to a object that was 'recently'
     * inserted at itself. The RM substrate algorithm is designed on a 
     * Pull model. This call however gives the RM substrate to implement
     * the Push model if it desires so in future. The current implementation
     * this method is non-operational since we believe that the Pull model 
     * behaves sufficiently well. 
     */
    public void registerKey(Id key) {
	// Currently we do nothing here. 

    }


    /**
     * Called by client(RMClient) to enable optimizations to route to the
     * nearest replica. Should be called by client in the context of the 
     * forward method of a lookup message. Should only be called if the local
     * client does not have the desired object. This call could change the 
     * nextHop field in the RouteMessage. 
     */
    public void lookupForward(RouteMessage msg) {
	Id target = msg.getTarget();
	int replicaFactor = rFactor;
	NodeSet set;
	int minProx;
	NodeHandle closestReplica;

	set = replicaSet(target, replicaFactor + 1);
	// We choose the best replica in terms of 'proximity' other than the self node
	set.remove(getNodeId());
	if(set.size()==0)
	    return;	
	closestReplica = set.get(0);
	minProx = closestReplica.proximity();
	for(int i= 1; i<set.size(); i++) {
	    NodeHandle nh;
	    int prox;
	    nh = set.get(i);
	    prox = nh.proximity();
	    if(prox < minProx) {
		minProx = prox;
		closestReplica = nh;
	    }
	}
	// We will change the nextHop Field of the RouteMessage to reflect this closest Replica
	msg.setNextHop(closestReplica);
	return;
    }
    
}











