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


/**
 * @(#) RMImpl.java
 *
 * This (Replica Manager Module) implements the RM interface. 
 * This runs as a Application which dynamically (in the presence of nodes
 * joining and leaving the network) maintains the invariant
 * that objects are replicated over the requested number of replicas.
 *
 * @version $Id$
 * @author Atul Singh
 * @author Animesh Nandi
 */


public class RMImpl extends PastryAppl implements RM {


    /**
     * Each PastryNode would have a single instance of RM. And all
     * applications requiring the services of RM would be passed 
     * instance of this RM in their constructor.
     */
    private static RMImpl _instance = null;

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
    private SendOptions _sendOptions;

    /**
     * Contains entries of the form [objectKey, (replicaFactor,Address)]. 
     * This table keeps the mapping of objectKeys to replicaFactor values.
     * The objectKeys correspond to objects that is local node is responsible
     * for, either by it being the root of the Replica set or any other 
     * node of the replica set.
     */
    private Hashtable _objects;

    /**
     * Contains entries of the form [appAddress, Application]
     * This table keeps the mapping of appAddress to Applications.
     * This is done to invoke the responsible() or notresponsible() 
     * upcalls in the corresponding Applications(which implement 
     * RMClient)
     */
    private Hashtable _appTable;

    private static class RMAddress implements Address {
	private int myCode = 0x8bed147c;
	
	public int hashCode() { return myCode; }

	public boolean equals(Object obj) {
	    return (obj instanceof RMAddress);
	}
    }


     /** 
     * Generic class used to keep state associated  with an object which was  replicated 
     * using our Replica Manager. RM has a hashtable, having key as objectKey 
     * of the object and value as a object of class Entry. Currently, we just
     * use (replicaFactor, Address) associated with the object as the state.
     */
    private class Entry{
	private int replicaFactor;
	private Address address;
	
	public Entry(int replicaFactor, Address address){
	    this.replicaFactor = replicaFactor;
	    this.address = address;
	}
	public int getreplicaFactor(){
	    return replicaFactor;
	}

	public Address getAddress() {
	    return address;
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
	_appTable = new Hashtable();
	_objects = new Hashtable();
	_credentials = new PermissiveCredentials();
	_sendOptions = new SendOptions();
    }

    /* Gets the local NodeHandle associated with this Scribe node.
     *
     * @return local handle of Scribe node.
     */
    public NodeHandle getLocalHandle() {
	return thePastryNode.getLocalHandle();
    }



    /**
     * Called by pastry when a message arrives for this application.
     * @param msg the message that is arriving.
     * @return void
     */
    public void messageForAppl(Message msg){
	RMMessage  rmmsg = (RMMessage)msg;
	int replicaFactor;
	Address rmdemuxaddress;
	RMClient rmclient;
	NodeId objectKey;
	Object object;


	if(rmmsg.getType() == RMMessage.RM_REPLICATE) {
	    // This a a message(asking for Replicating an object) that
	    // was routed through pastry and delivered to this node
	    // which is the closest to the objectKey
	    
	    int i;
	    LeafSet leafSet;
	    Vector sortedleafSet;

	    objectKey = rmmsg.getobjectKey();
	    object = rmmsg.getObject();
	    replicaFactor = rmmsg.getreplicaFactor();
	    rmdemuxaddress = rmmsg.getAddress();
	    
	    System.out.println("REPLICATE:: Leader id "+getNodeId());
	    
	    leafSet = getLeafSet();
	    
	    sortedleafSet = (Vector)closestReplicas(leafSet, objectKey);
	    sortedleafSet = compactReplicaSet(sortedleafSet, replicaFactor);
	    
	    /* Here we know that we have at most "replicaFactor" number of
	     * NodeId's in the sortedLeafSet. So, now we should send "insert"
	     * messages to these leafSet members.
	     */
	    System.out.println("REPLICATE::Number of replicas "+sortedleafSet.size());
	    // We first insert the object into the Primary replica node 
	    // that is itself, and then replicate additionally to replicaFactor nodes
	    msg = new RMMessage(getAddress(), object, objectKey, RMMessage.RM_INSERT, replicaFactor, rmdemuxaddress, getCredentials());
	    System.out.println("Replicating object"+objectKey+" to "+ getNodeId());
	    routeMsgDirect(getLocalHandle(), msg, getCredentials(), _sendOptions);
	    
	    for(i=0; i < sortedleafSet.size(); i++){
		msg = new RMMessage(getAddress(), object, objectKey, RMMessage.RM_INSERT, replicaFactor, rmdemuxaddress, getCredentials());
		System.out.println("Replicating object"+objectKey+" to "+((NodeId)sortedleafSet.elementAt(i)));
		routeMsgDirect(leafSet.get((NodeId)sortedleafSet.elementAt(i)), msg, getCredentials(), _sendOptions);
	    }
	}
	
	if(rmmsg.getType() == RMMessage.RM_HEARTBEAT) {
	    // This a a message(asking for Refreshing an object) that
	    // was routed through pastry and delivered to this node
	    // which is the closest to the objectKey
	    
	    int i;
	    LeafSet leafSet;
	    Vector sortedleafSet;

	    objectKey = rmmsg.getobjectKey();
	    replicaFactor = rmmsg.getreplicaFactor();
	    rmdemuxaddress = rmmsg.getAddress();
	    
	    System.out.println("HEARTBEAT:: Leader id "+getNodeId());
	    
	    leafSet = getLeafSet();
	    
	    sortedleafSet = (Vector)closestReplicas(leafSet, objectKey);
	    sortedleafSet = compactReplicaSet(sortedleafSet, replicaFactor);
	    
	    /* Here we know that we have at most "replicaFactor" number of
	     * NodeId's in the sortedLeafSet. So, now we should send "refresh"
	     * messages to these leafSet members.
	     */
	    System.out.println("HEARTBEAT::Number of replicas "+sortedleafSet.size());
	    // We first refresh the object into the Primary replica node 
	    // that is itself, and then refresh additionally to replicaFactor nodes
	    msg = new RMMessage(getAddress(), null, objectKey, RMMessage.RM_REFRESH, replicaFactor, rmdemuxaddress, getCredentials());
	    System.out.println("Refreshing object"+objectKey+" to "+ getNodeId());
	    routeMsgDirect(getLocalHandle(), msg, getCredentials(), _sendOptions);
	    
	    for(i=0; i < sortedleafSet.size(); i++){
		msg = new RMMessage(getAddress(), null, objectKey, RMMessage.RM_REFRESH, replicaFactor, rmdemuxaddress, getCredentials());
		System.out.println("Refreshing object"+objectKey+" to "+((NodeId)sortedleafSet.elementAt(i)));
		routeMsgDirect(leafSet.get((NodeId)sortedleafSet.elementAt(i)), msg, getCredentials(), _sendOptions);
	    }
	}
	
	if(rmmsg.getType() == RMMessage.RM_REMOVE) {
	    // This a a message(asking for removing an object) that
	    // was routed through pastry and delivered to this node
	    // which is the closest to the objectKey
	    
	    int i;
	    LeafSet leafSet;
	    Vector sortedleafSet;
	

	    objectKey = rmmsg.getobjectKey();
	    object = rmmsg.getObject();
	    replicaFactor = rmmsg.getreplicaFactor();
	    rmdemuxaddress  = rmmsg.getAddress();

	    System.out.println("REMOVE:: Leader id "+getNodeId());

	    leafSet = getLeafSet();
		
	    sortedleafSet = (Vector)closestReplicas(leafSet, objectKey);
	    sortedleafSet = compactReplicaSet(sortedleafSet, replicaFactor);
	    
		
	    /* Here we know that we have at most "replicaFactor" 
	     * number of NodeId's in the sortedLeafSet. So, now we
	     * should send "delete" messages to these leafSet members.
	     */
	    System.out.println("REMOVE::Number of replicas "+sortedleafSet.size());
	    
	    // We first remove the object into the Primary replica node 
	    // that is itself, and then remove additionally from replicaFactor nodes
	    msg = new RMMessage(getAddress(), object, objectKey, RMMessage.RM_DELETE, replicaFactor, rmdemuxaddress, getCredentials());
	    System.out.println("Removing object"+objectKey+" from "+ getNodeId());
	    routeMsgDirect(getLocalHandle(), msg, getCredentials(), _sendOptions);
	    
	    
	    for(i=0; i < sortedleafSet.size(); i++){
		msg = new RMMessage(getAddress(), null, objectKey, RMMessage.RM_DELETE, replicaFactor, rmdemuxaddress, getCredentials());
		System.out.println("Removing object" + objectKey + " from " +((NodeId)sortedleafSet.elementAt(i)));
		routeMsgDirect(leafSet.get((NodeId)sortedleafSet.elementAt(i)), msg, getCredentials(), _sendOptions);
	    }
	}

	
	if(rmmsg.getType() == RMMessage.RM_INSERT){
	    // This is a local insert
	    objectKey = rmmsg.getobjectKey();
	    object = rmmsg.getObject();
	    rmdemuxaddress = rmmsg.getAddress();
	    System.out.println("RM_INSERT:: received replica message for objectKey "+ objectKey+" at local node "+getNodeId());
	    replicaFactor = rmmsg.getreplicaFactor();
	    if(_objects.put(objectKey, new Entry(replicaFactor,rmdemuxaddress)) != null)
		System.out.println("RM_INSERT:: ERRR... insert called for object which already exists in the system.\n");
	    else {
		// Call responsible() on the corresponding application
		rmclient = (RMClient)_appTable.get(rmdemuxaddress);
		rmclient.responsible(objectKey, object);
	    }
	    
	}

	if(rmmsg.getType() == RMMessage.RM_REFRESH){
	    // This is a local refresh
	    objectKey = rmmsg.getobjectKey();
	    rmdemuxaddress = rmmsg.getAddress();
	    System.out.println("RM_REFRESH:: received replica message for objectKey "+ objectKey+" at local node "+getNodeId());
	    replicaFactor = rmmsg.getreplicaFactor();
	    if(!_objects.containsKey(objectKey))
		System.out.println("RM_REFRESH:: ERRR... refresh called for object which did not exist in the system.\n");
	    
	    // Call refresh() on the corresponding application
	    rmclient = (RMClient)_appTable.get(rmdemuxaddress);
	    rmclient.refresh(objectKey);
	}

	if(rmmsg.getType() == RMMessage.RM_DELETE){
	    // This is a local delete
	    objectKey = rmmsg.getobjectKey();
	    object = rmmsg.getObject();
	    rmdemuxaddress = rmmsg.getAddress();
	    System.out.println("RM_DELETE:: receieved replica message for objectKey " + objectKey +" at localnode "+getNodeId());
	    if(_objects.remove(objectKey) == null)
		System.out.println("RM_DELETE:: ERRR... delete called for object not in the system. objectKey "+ objectKey);
	    else {

		// Call notresponsible() on the corresponding application
		rmclient = (RMClient)_appTable.get(rmdemuxaddress);
		rmclient.notresponsible(objectKey);


	    }
	    
	}
    }

  
    /**
     * Registers the application to the RM.
     * @param appAddress the application's address
     * @param app the application, which is an instance of ReplicaClient
     */
    public boolean register(Address appAddress, RMClient app) {
	// Keep the appAddress-application mapping in hashtable
	_appTable.put(appAddress, app);
	return true;
    }

     /**
     * Called by the application when it needs to replicate an object into k nodes
     * closest to the object key.
     *
     * @param appAddress applications address which calls this method
     * @param objectKey  the pastry key for the object
     * @param object the object
     * @param replicaFactor the number of nodes k into which the object is replicated
     * @return true if operation successful else false
     */
    public boolean replicate(Address appAddress, NodeId objectKey, Object object, int replicaFactor) {
	RMMessage msg = new RMMessage(getAddress(),object,objectKey,RMMessage.RM_REPLICATE, replicaFactor, appAddress, getCredentials());

	this.routeMsg(objectKey, msg, _credentials , _sendOptions);
	return true;

    }



    /**
     * Called by the application when it needs to refresh an object into k nodes
     * closest to the object key.
     *
     * @param appAddress applications address which calls this method
     * @param objectKey  the pastry key for the object
     * @param replicaFactor the number of nodes k into which the object is replicated
     * @return true if operation successful else false
     */
    public boolean heartbeat(Address appAddress, NodeId objectKey, int replicaFactor) {
	RMMessage msg = new RMMessage(getAddress(),null,objectKey,RMMessage.RM_HEARTBEAT, replicaFactor, appAddress, getCredentials());

	this.routeMsg(objectKey, msg, _credentials , _sendOptions);
	return true;
    }

    /**
     * Called by applications when it needs to remove this object from k nodes 
     * closest to the objectKey. 
     *
     * @param appAddress applications address
     * @param objectKey  the pastry key for the object
     * @param replicaFactor the replication factor for the object
     * @return true if operation successful
     */
    public boolean remove(Address appAddress, NodeId objectKey, int replicaFactor) {
	RMMessage msg = new RMMessage(getAddress(),null,objectKey,RMMessage.RM_REMOVE, replicaFactor, appAddress, getCredentials());

	this.routeMsg(objectKey, msg, _credentials , _sendOptions);
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
     * This function sorts the leafSet according to the distance of its elements
     * from objectKey in the NodeId space. Returns a vector of sorted NodeIds,
     * which may contain duplicate entries.
     * 
     * @param leafSet leafSet of this node.
     * @param localNodeId nodeId of the this node
     * @return a sorted vector of NodeIds.
     */
    private Vector closestReplicas(LeafSet leafSet, NodeId objectKey)
    {
	int i;
	Vector sortedleafSet = new Vector();
	int cwSize, ccwSize;
	NodeId current;

	cwSize = leafSet.cwSize();
	ccwSize = leafSet.ccwSize();

	for(i = 1; i <= cwSize; i++){
	    current = leafSet.get(i).getNodeId();
	    sortedleafSet = insertionSort(sortedleafSet, current, objectKey);
	}
	
	for(i = 1; i <= ccwSize; i++){
	    current = leafSet.get(-i).getNodeId();
	    sortedleafSet = insertionSort(sortedleafSet, current, objectKey);
	}
	return sortedleafSet;
    }
    
    /**
     * This function removes duplicate entries from the sorted set of 
     * NodeIds.
     * @param sortedleafSet input sorted set
     * @param size the number of  distinct entries required
     * @return the sorted set having "size" number of distinct nodeIds
     */
    private Vector compactReplicaSet(Vector sortedleafSet, int size)
    {
	/* Do compaction. Check if two node ids which are equi-distant from
	 * the objectKey  are same node or not?? If they are same, remove one of them.
	 */
	int i = 0;
	NodeId current, next;
	
	while((i< size) && (i  < (sortedleafSet.size() - 1))){
	    current = ((NodeId)sortedleafSet.elementAt(i));
	    next = ((NodeId)sortedleafSet.elementAt(i+1));
	    if(current.compareTo(next) == 0){
		sortedleafSet.remove(i+1);
	    }
	    else
		i++;
	}
	while (sortedleafSet.size() > i)
	    sortedleafSet.remove(i);
	return sortedleafSet;
    }


    /**
     * This function implements the insertionSort sorting algorithm over
     * the set "set", which is a vector of NodeIds.
     *
     * @param set the sorted set
     * @param nodeId new nodeId to put in correct place in the sorted set
     * @param objectKey distance (in nodeId space) from this argument is the sorting metric
     * @return the new sorted set 
     */
    private Vector insertionSort(Vector set, NodeId nodeId, NodeId objectKey)
    {
	int size;
	int i;
	NodeId.Distance distance1, distance2;

	size = set.size();
	distance1 = objectKey.distance(nodeId);

	for(i=0; i<size; i++){
	    distance2 = objectKey.distance((NodeId)set.elementAt(i));
	    if(distance1.compareTo(distance2) < 0){
		set.insertElementAt(nodeId, i);
		return set;
	    }
	    else if(distance1.compareTo(distance2) == 0){
		if(nodeId.compareTo((NodeId)set.elementAt(i)) <= 0){
		    set.insertElementAt(nodeId, i);
		    return set;
		}
	    }
	}
	set.insertElementAt(nodeId, size);
	return set;
    }

    /**
     * Implements the main algorithm for keeping the invariant that an object 
     * would  be stored in k closest nodes to the objectKey  while the nodes are
     * coming up or going down. 
     * @param nh NodeHandle of the node which caused the leafSet change
     * @param wasAdded true if added, false if removed
     * @return void
     */
    public void leafSetChange(NodeHandle nh, boolean wasAdded)
    {
	Vector uncompactedReplicas,replicas ;
	LeafSet leafSet ;
	NodeId objectKey;
	int replicaFactor;
	Address rmdemuxaddress;
	RMMessage insertmsg, deletemsg;
	Enumeration keys = _objects.keys();	

	//if(DirectRMRegrTest.setupDone)
	//  System.out.println("leafSetChange invoked on " + getNodeId());
	
	leafSet = getLeafSet();
	
	if( wasAdded){
	    /* A new node was added */ 

	    while(keys.hasMoreElements()){

		objectKey = (NodeId)keys.nextElement();
		replicaFactor =((Entry)_objects.get(objectKey)).getreplicaFactor();
		rmdemuxaddress =((Entry)_objects.get(objectKey)).getAddress();
		uncompactedReplicas = closestReplicas(leafSet, objectKey);
		replicas = compactReplicaSet((Vector)uncompactedReplicas.clone(), (replicaFactor + 1));
		/*
		System.out.println("********** ******************************************");
		System.out.println("At localnode "+getNodeId()+" LeafSet is "+leafSet.toString());
		System.out.println("Node "+nh.getNodeId()+"was changed in the leafSet");
		System.out.println("wasAdded is "+wasAdded);
		System.out.println(" #keys = " + _objects.size());
		System.out.println("objectKey ="+objectKey);
		System.out.println("** UncompactedReplicaSet Starts **");   
		printReplicaSet(uncompactedReplicas);
		System.out.println("** UncompactedReplicaSet Ends **");  
		System.out.println("** compactedReplicaSet Starts **");   
		printReplicaSet(replicas);
		System.out.println("** compactedReplicaSet Ends **");  
		if(checkLeader(replicas, getNodeId(), objectKey)){
		    System.out.println("checkLeader returned true");
		}
		else{
		    System.out.println("checkLeader returned false");
		    if(checkSecondaryLeader(replicas, getNodeId(), objectKey))
			System.out.println("checkSecondLeader returned true");
		    else
			System.out.println("checkSecondLeader returned false");
		}
		*/

		if(checkLeader(replicas, getNodeId(), objectKey)){
		    /* I am the Leader node for this object, so its my responsibility
		     * to maintain the invariant that this object is replicated into
		     * "replicaFactor" number of nodes.
		     */
		    if(!addedNodeWithinRange(replicas, nh.getNodeId(), replicaFactor, objectKey))
			continue;
		    
		    if(addedNodeExistsTwiceInUncompactedReplicaSet(uncompactedReplicas,nh.getNodeId()))
			continue;
		    else 
			{
			    System.out.println("I [" + getNodeId() + "] am the leader node");
			    /* Clearly, we need to ship this objects to this "nh" node
			     * and then a delete message to the node with rank
			     * "replicaFactor +1".
			     */
			    
			    
			    insertmsg =  new RMMessage(getAddress(), null, objectKey, RMMessage.RM_INSERT, replicaFactor, rmdemuxaddress, getCredentials());

			    System.out.println("Replicating objectKey "+objectKey+" into node"+nh.getNodeId()+" from localNode"+getNodeId());
			    routeMsgDirect(nh, insertmsg, getCredentials(), _sendOptions);
			    if(replicas.size() >= (replicaFactor +1)){
				deletemsg =  new RMMessage(getAddress(), null, objectKey, RMMessage.RM_DELETE, replicaFactor, rmdemuxaddress, getCredentials());
				System.out.println("Deleting objectKey "+objectKey+"from replicaNode "+((NodeId)replicas.elementAt(replicaFactor))+" from localNodeId"+getNodeId());
				routeMsgDirect(leafSet.get((NodeId)(replicas.elementAt(replicaFactor))), deletemsg, getCredentials(), _sendOptions);
			    }
			}
		}
		else if(checkSecondaryLeader(replicas, getNodeId(), objectKey)){
		    if(!(nh.getNodeId()).equals(replicas.elementAt(0)))
			continue;
		     if(addedNodeExistsTwiceInUncompactedReplicaSet(uncompactedReplicas,nh.getNodeId()))
			continue;
		    System.out.println("I [" + getNodeId() + "] am the secondaryleader node");
		    
		    

		    insertmsg =  new RMMessage(getAddress(), null, objectKey, RMMessage.RM_INSERT, replicaFactor, rmdemuxaddress, getCredentials());
		    System.out.println("Replicating objectKey "+objectKey+" into node"+nh.getNodeId()+" from localNode "+getNodeId());
		    routeMsgDirect(nh, insertmsg, getCredentials(), _sendOptions);
		    
		    if(replicas.size() >= (replicaFactor +1)){
			deletemsg =  new RMMessage(getAddress(), null, objectKey, RMMessage.RM_DELETE, replicaFactor, rmdemuxaddress, getCredentials());
			System.out.println("Deleting objectKey "+objectKey+" from replicaNode "+((NodeId)replicas.elementAt(replicaFactor))+" at localNode "+getNodeId());
			routeMsgDirect(leafSet.get((NodeId)(replicas.elementAt(replicaFactor))), deletemsg, getCredentials(), _sendOptions);
		    }
		}
		//System.out.println("****************************************************");
	    
	    }
	}
	else
	    { 
		/* A node was deleted from the leafSet. */

		while(keys.hasMoreElements()){

		    objectKey = (NodeId)keys.nextElement();
		    replicaFactor =((Entry)_objects.get(objectKey)).getreplicaFactor();
		    rmdemuxaddress =((Entry)_objects.get(objectKey)).getAddress();

		    uncompactedReplicas = closestReplicas(leafSet, objectKey);
		    replicas = compactReplicaSet(uncompactedReplicas, replicaFactor);

		    /*
		    System.out.println("****************************************************");
		    System.out.println("At localnode "+getNodeId()+" LeafSet is "+leafSet.toString());
		    System.out.println("Node "+nh.getNodeId()+"was changed in the leafSet");
		    System.out.println("wasAdded is "+wasAdded);
		    System.out.println(" #keys = " + _objects.size());
		    System.out.println("objectKey ="+objectKey);
		    System.out.println("** UncompactedReplicaSet Starts **");   
		    printReplicaSet(uncompactedReplicas);
		    System.out.println("** UncompactedReplicaSet Ends **");  
		    System.out.println("** compactedReplicaSet Starts **");   
		    printReplicaSet(replicas);
		    System.out.println("** compactedReplicaSet Ends **");  
		    if(checkLeader(replicas, getNodeId(), objectKey)){
			System.out.println("checkLeader returned true");
		    }
		    else{
			System.out.println("checkLeader returned false");
		    }
		    */

		    if(checkLeader(replicas, getNodeId(), objectKey)){
			/* I am the Leader node for this object, so its my responsibility
			 * to maintain the invariant that this object is replicated into
			 * "replicaFactor" number of nodes.
			 */
			if(!deletedNodeWithinRange(replicas, nh.getNodeId(), replicaFactor, objectKey))
			    continue;
			if(deletedNodeExistsInReplicaSet(replicas, nh.getNodeId(), replicaFactor))
			    continue;

			insertmsg =  new RMMessage(getAddress(), null, objectKey, RMMessage.RM_INSERT, replicaFactor, rmdemuxaddress, getCredentials());
			System.out.println("Replicating objectKey "+objectKey+" into node"+((NodeId)(replicas.elementAt(replicaFactor -1)))+" at localNode "+getNodeId());
			routeMsgDirect(leafSet.get((NodeId)(replicas.elementAt(replicaFactor -1))), insertmsg, getCredentials(), _sendOptions);

		    }
		    //System.out.println("***************************************");
		}
	    }
    }

    /**
     * This function finds out whether the local node is the leaderNode for the 
     * object associated with this objectKey. A node is a leaderNode when it is closest
     * to the objectKey in the nodeId space. The leaderNode is responsible for maintaining
     * the invariant that the given object stays replicated over its required number
     * of replicas.
     * @param replicas a sorted set of distinct NodeIds
     * @param localNodeId nodeId of this node
     * @param objectKey pastry key of the object
     * @return true if this node is the LeaderNode for the objectKey.
     */
    private boolean checkLeader(Vector replicas, NodeId localNodeId, NodeId objectKey)
    {
	NodeId first = (NodeId) replicas.elementAt(0);
	NodeId.Distance firstDistance, myDistance;

	if(replicas.size() == 0)
	    return true;

	firstDistance = objectKey.distance(first);
	myDistance = objectKey.distance(localNodeId);
	
	if(myDistance.compareTo(firstDistance) < 0)
	    return true;
	else if(myDistance.compareTo(firstDistance) >0)
	    return false;
	else {
	    if(localNodeId.compareTo(first) < 0)
		return true;
	    else
		return false;
	}	
    }


    /**
     * This function finds out whether the local node is the secondaryleaderNode for the 
     * object associated with this objectKey. A node is a secondaryleaderNode when it is
     * second closest to the objectKey in the nodeId space. RM needs the services of 
     * secondary leader when the newly added node in the system is the leaderNode for some 
     * of the objects stored in the system. Only the secondaryleaderNode is responsible for
     * shipping these objects to the newly added leaderNode.
     *
     * @param replicas a sorted set of distinct NodeIds
     * @param localNodeId nodeId of this node
     * @param objectKey pastry key of the object
     * @return true if this node is the SecondaryLeaderNode for the objectKey.
     */
    private boolean checkSecondaryLeader(Vector replicas, NodeId localNodeId, NodeId objectKey)
    {
	NodeId second; 
	NodeId.Distance  secondDistance, myDistance;

	/* Assumes that checkLeader() has been called already and it failed. So,
	 * we are sure that there is at least 1 entry in the vector.
	 */
	if(replicas.size() == 1)
	    return true;
	
	second = (NodeId) replicas.elementAt(1);
	secondDistance = objectKey.distance(second);
	myDistance = objectKey.distance(localNodeId);
	
	if(myDistance.compareTo(secondDistance) < 0)
	    //{System.out.println("Returning true in checkSecondaryLeader"); return true;}
	    return true;
	else if(myDistance.compareTo(secondDistance) >0)
	    //{System.out.println("Returning false in checkSecondaryLeader"); return false;}
	    return false;
	else {
	    if(localNodeId.compareTo(second) < 0)
		//{System.out.println("Returning true in checkSecondaryLeader"); return true;}
		return true;
	    else
		//{System.out.println("Returning false in checkSecondaryLeader"); return false;}
		return false;

	}	
    }

   

    /**
     * This function checks whether the newly added node is one of the replicaFactor 
     * closest nodes to the objectKey in the nodeId space.
     *
     * @param replicas a sorted set of distinct NodeIds
     * @param nodeId nodeId of added node
     * @param replicaFactor number of replicas associated with this objectKey
     * @param objectKey pastry key of the object
     * @return true if addedNode is one of the replicaFactor closest node to objectKey
     */
    private boolean addedNodeWithinRange(Vector replicas, NodeId nodeId, int replicaFactor, NodeId objectKey)
	{
	    NodeId temp;
	    NodeId.Distance tempDistance, distance;

	    if(replicas.size() <= replicaFactor)
		return true;
	    temp = (NodeId)replicas.elementAt(replicaFactor - 1);
	    tempDistance = objectKey.distance(temp);
	    distance = objectKey.distance(nodeId);
	    if(tempDistance.compareTo(distance) < 0)
		return false;
	    else if(tempDistance.compareTo(distance) > 0)
		return true;
	    else{
		if(temp.compareTo(nodeId) < 0)
		    return false;
		else
		    return true;
	    }
	}

    /**
     * This function checks whether the newly removed node was one of the replicaFactor 
     * closest nodes to the objectKey in the nodeId space.
     *
     * @param replicas a sorted set of distinct NodeIds
     * @param nodeId nodeId of removed node
     * @param replicaFactor number of replicas associated with this objectKey
     * @param objectKey pastry key of the object
     * @return true if deletedNode was one of the replicaFactor closest node to objectKey
     */
    private boolean deletedNodeWithinRange(Vector replicas, NodeId nodeId, int replicaFactor, NodeId objectKey)
    {
	NodeId temp;
	NodeId.Distance tempDistance, distance;

	if(replicas.size() < replicaFactor)
	    return false;
	
	temp = (NodeId)replicas.elementAt(replicaFactor - 1);
	tempDistance = objectKey.distance(temp);
	distance = objectKey.distance(nodeId);
	if(tempDistance.compareTo(distance) < 0)
	    return false;
	else if(tempDistance.compareTo(distance) > 0)
	    return true;
	else{
	    if(temp.compareTo(nodeId) < 0)
		return false;
	    else
		return true;
	}
    }

    /**
     * This function checks whether the node which is removed from one half of leafSet
     * (which caused the leafSet change) is still in the other half of leafSet. This occurs
     * when deleted node had previously two entries in the leafSet.
     *
     * @param replicas a sorted set of distinct NodeIds
     * @param deletedNodeId nodeId of removed node
     * @param replicaFactor number of replicas associated with this objectKey
     * @return true if deletedNode still exists in the replica set
     */
    private boolean deletedNodeExistsInReplicaSet(Vector replicas, NodeId deletedNodeId, int replicaFactor)
    {
	int count = 0;
	NodeId temp;

	while(count < replicaFactor){
	    temp = (NodeId)replicas.elementAt(count);
	    if(temp.equals(deletedNodeId) == true)
		return true;
	    count++;
	}
	return false;
    }


    /**
     * This function checks whether the  node which is added in one half of leafSet
     * (which caused the leafSet change) is already present in the other half of leafSet.
     * This occurs when added node currently has two entries in the leafSet.
     *
     * @param set a sorted set of NodeIds (can have duplicate entries)
     * @param nodeId nodeId of added node
     * @return true if addedNode occurs twice in leafSet
     */
    private boolean addedNodeExistsTwiceInUncompactedReplicaSet(Vector set, NodeId nodeId)
    {
	int i;
	int count= 0;
	for (i=0; i < set.size(); i++) {
	    if(!(((NodeId)set.elementAt(i)).equals(nodeId)) && count==1) return false;
	    if(((NodeId)set.elementAt(i)).equals(nodeId))
		count++;
	    if (count==2) break;
	}
	if(count == 2)
	    return true;
	else
	    return false;

    }

    /**
     * Prints the sorted replica set containing nodeIds.
     * @param set set of NodeIds
     * @return void
     */
    private void printReplicaSet(Vector set)
    {
	int i= 0;
	
	while(i<set.size())
	    System.out.print((NodeId)set.elementAt(i++) +" , ");
	System.out.println(" ");

    }



}






