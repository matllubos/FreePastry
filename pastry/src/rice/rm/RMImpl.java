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

import rice.storage.*;

import ObjectWeb.Persistence.*;

/**
 * @(#) RMImpl.java
 *
 * This (Replica Manager Module) implements the RM interface. This runs as a Application which
 *  dynamically (in the presence of nodes joining and leaving the network) maintains the invariant
 *  that objects are replicated over the requested number of replicas.
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
    private static RMAddress _instance_RMAddress = new RMAddress();

    /**
     * This table keeps the mapping of applications to replicaFactor values
     * that these applications requested during registering with RM. 
     */
    private Hashtable _appTable;

    /**
     * Credentials for this application.
     */
    private Credentials _credentials;

    /**
     * SendOptions to be used on pastry messages.
     */
    private SendOptions _sendOptions;

    /**
     * Contains entries of the form [objectKey, replicaFactor]. 
     */
    private Hashtable _objects;


    /**
     * instance of Storage Module
     */
    private StorageManager _sm;


    /**
     * Constructor : Builds a new ReplicaManager(RM) associated with this 
     * pastryNode.
     * @param pn the PastryNode associated with this application
     * @return void
     */
    public RMImpl(PastryNode pn, StorageManager sm)
    {
	super(pn);
	_sm = sm;
	_appTable = new Hashtable();
	_objects = new Hashtable();
	_credentials = new PermissiveCredentials();
	_sendOptions = new SendOptions();
    }

    /**
     * Called by pastry when a message arrives for this application.
     * @param msg the message that is arriving.
     * @return void
     */
    public void messageForAppl(Message msg){
	RMMessage  rmmsg = (RMMessage)msg;
	int replicaFactor;
	
	if(rmmsg.getType() == RMMessage.RM_INSERT){
	    System.out.println("RM_INSERT:: receieved replica message for objectKey "+rmmsg.getobjectKey()+" at local node "+getNodeId());
	    replicaFactor = rmmsg.getreplicaFactor();
	    if(_objects.put(rmmsg.getobjectKey(), new Entry(replicaFactor)) != null)
		System.out.println("RM_INSERT:: ERRR... insert called for object which already exists in the system.\n");
	    else {
		/**** Call the StorageManager's corresponding function for storing
		 * this object at the local node.
		 */
		if(rmmsg.getObject() instanceof Persistable){
		    if(!insertPersistableObject((Persistable)rmmsg.getObject(), rmmsg.getobjectKey(), rmmsg.getCredentials()))
			System.out.println("RM_INSERT:: ERRR... objectType is Persistable");
		}
		else if(rmmsg.getObject() instanceof StorageObject){
		    if(!insertStorageObject((StorageObject)rmmsg.getObject(), rmmsg.getobjectKey()))
			System.out.println("RM_INSERT:: ERRR... objectType is StorageObject");
		}
		else
		    System.out.println("RM_INSERT:: ERR.. object is neither Persistable nor StorageObject"); 
	    }
	    
	}
	if(rmmsg.getType() == RMMessage.RM_DELETE){
	    System.out.println("RM_DELETE:: receieved replica message for objectKey "+rmmsg.getobjectKey()+" at localnode "+getNodeId());
	    if(_objects.remove(rmmsg.getobjectKey()) == null)
		System.out.println("RM_DELETE:: ERRR... delete called for object not in the system. objectKey "+rmmsg.getobjectKey());
	    else {
		/**** Call the StorageManager's corresponding function for deleting 
		 * this object at the local node.
		 */
		if(rmmsg.getObject() instanceof Persistable){
		    if(!deletePersistableObject( rmmsg.getobjectKey(), rmmsg.getCredentials()))
			System.out.println("RM_DELETE:: ERRR... SM couldnt store :: objectType is Persistable");
		}
		else if(rmmsg.getObject() instanceof StorageObject){
		    if(!deleteStorageObject((StorageObject)rmmsg.getObject(),rmmsg.getobjectKey()))
			System.out.println("RM_DELETE:: ERRR...SM couldnt store:: objectType is StorageObject");
		}
		else
		    System.out.println("RM_DELETE:: ERR.. Object is neither Persistable nor StorageObject"); 

	    }
	    
	}
	if(rmmsg.getType() == RMMessage.RM_UPDATE){
	    System.out.println("RM_UPDATE:: receieved replica message for objectKey "+rmmsg.getobjectKey()+" at localNode "+getNodeId());
	    if(!_objects.containsKey(rmmsg.getobjectKey()))
		System.out.println("RM_UPDATE:: ERRRR... update called for object not in the system.\n");
	
	    else {
		/**** Call the StorageManager's corresponding function for updating
		 *   this object at the local node.
		 */
		if(rmmsg.getObject() instanceof Persistable){
		    if(!updatePersistableObject((Persistable)rmmsg.getObject(), rmmsg.getobjectKey()))
			System.out.println("RM_UPDATE:: ERR..SM couldnt store :: object type is persistable");
		}
		else
		    System.out.println("RM_UPDATE:: ERR.. object type is not Persistable");
	    }
	} 
    }

  
    /**
     * Registers the application to the RM.
     * @param appAddress the application's address
     * @param replicaFactor the number of replicas this application needs for its objects
     * @return false if replicaFactor is greater than the permitted value(maxleafsetsize/2 + 1)
     *         else true.
     */
    public boolean Register(Address appAddress, int replicaFactor) {
	if (replicaFactor > ((getLeafSet().maxSize()/2 + 1)))
	    return false;

	// Keep the application-replicaFactor mapping in hashtable
	_appTable.put(appAddress, new Integer(replicaFactor));
	return true;

    }


    /**
     * Return the Storage Manager associated with this pastry node
     * @return instance of Storage Manager
     */
    private StorageManager getStorageManager()
    {
	return _sm;
    }



    
    /**
     * Returns the address of this application.
     * @return the address.
     */
    public Address getAddress() {
	return _instance_RMAddress;
    }

    /**
     * Returns the credentials for the application
     * @return the credentials
     */
    public Credentials getCredentials() {
	return this._credentials;
    }



  /**
   * Called by applications when it needs to insert this object into k nodes 
   * closest to the objectKey. The k is the replicaFactor with which this application 
   * previously registered with RM.  The application should correctly call this
   * method in the sense that this pastryNode should CURRENTLY be closest node to the
   * objectKey in the nodeId space, otherwise insert returns false. When this call returns it is
   * not guaranteed that the object gets stored in all the replicas
   * instantaneously, what it simply does is issues requests to all those
   * concerned nodes to insert the object. For the time being, "object" should
   * implement ObjectWeb.Persistence.Persistable interface.
   *
   * @param appAddress applications address which calls this method
   * @param objectKey  the pastry key for the object
   * @param object the object (Currently, should implement Persistable)
   * @param authorCred the credentials of the author
   * @return true if operation successful else false
   */
  public boolean insert(Address appAddress, NodeId objectKey, Persistable object, Credentials authorCred)
    {

	int replicaFactor;
	int i;
	LeafSet leafSet;
	Vector sortedleafSet;
	RMMessage msg;

	System.out.println("INSERT:: Leader id "+getNodeId());
	replicaFactor = ((Integer) (_appTable.get(appAddress))).intValue();
	if(_objects.put(objectKey, new Entry(replicaFactor)) != null){
	    System.out.println("RM_INSERT:: ERRR... insert called for object which already exists in the system.\n");
	    return false;
	}

	leafSet = getLeafSet();

	sortedleafSet = (Vector)closestReplicas(leafSet, objectKey);
	sortedleafSet = compactReplicaSet(sortedleafSet, replicaFactor);

	/* Here we know that we have at most "replicaFactor" number of NodeId's 
	 * in the sortedLeafSet. So, now we should send "insert" messages to
	 * these leafSet members.
	 */
	System.out.println("INSERT::Number of replicas "+sortedleafSet.size());
	for(i=0; i < sortedleafSet.size(); i++){
	    msg = new RMMessage(getAddress(), object, objectKey, RMMessage.RM_INSERT, replicaFactor, authorCred);
	    System.out.println("Replicating objectKey"+objectKey+" to "+((NodeId)sortedleafSet.elementAt(i)));
	    routeMsg((NodeId)sortedleafSet.elementAt(i), msg, getCredentials(), _sendOptions);
	}
	return true;
    }


 /**
   * Called by applications when it needs to delete this object from k nodes 
   * closest to the objectKey. The k is the replicaFactor with which this application 
   * previously registered with RM.  The application should correctly call this
   * method in the sense that this pastryNode should CURRENTLY be closest node to the
   * objectKey in the nodeId space, otherwise delete returns false. When this call 
   * returns it is not guaranteed that the object gets deleted in all the replicas
   * instantaneously, what it simply does is issues requests to all those
   * concerned nodes to delete the object.  
   *
   * @param appAddress applications address
   * @param objectKey  the pastry key for the object
   * @param authorCred the credentials of the author
   * @return true if operation successful
   */
    public boolean delete(Address appAddress, NodeId objectKey, Credentials authorCred)
    {
	int replicaFactor;
	int i;
	LeafSet leafSet;
	Vector sortedleafSet;
	RMMessage msg;
	
	replicaFactor = ((Integer) (_appTable.get(appAddress))).intValue();
	if(_objects.remove(objectKey) == null){
	    System.out.println("RM_DELETE:: ERRR... delete called for object not in the system.\n");
	    return false;
	}
	leafSet = getLeafSet();
	
	sortedleafSet = (Vector)closestReplicas(leafSet, objectKey);
	sortedleafSet = compactReplicaSet(sortedleafSet, replicaFactor);

	
	/* Here we know that we have at most "replicaFactor" number of NodeId's 
	 * in the sortedLeafSet. So, now we should send "delete" messages to
	 * these leafSet members.
	 */
	System.out.println("DELETE::Number of replicas "+sortedleafSet.size());
	for(i=0; i < sortedleafSet.size(); i++){
	    msg = new RMMessage(getAddress(), null, objectKey, RMMessage.RM_DELETE, replicaFactor, authorCred);
	    //System.out.println("Replicating to "+((NodeId)sortedleafSet.elementAt(i)));
	    routeMsg((NodeId)sortedleafSet.elementAt(i), msg, getCredentials(), _sendOptions);
	}
	return true;
    }                                  


  /**
   * Called by applications when it needs to update this object into k nodes 
   * closest to the objectKey. The k is the replicaFactor with which this application 
   * previously registered with RM. The application should correctly call this
   * method in the sense that this pastryNode should CURRENTLY be closest node to the
   * objectKey in the nodeId space, otherwise update returns false. When this 
   * call returns it is not guaranteed that the object gets Updated in all the replicas
   * instantaneously, what it simply does is issues requests to all those
   * concerned nodes to update the object. For the time being, "object" should
   * implement ObjectWeb.Persistence.Persistable interface.
   *
   * @param appAddress application's address
   * @param objectKey  the pastry key of the object
   * @param object the object (Currently, should implement Persistable)
   * @return true if operation successful
   */
    public boolean update(Address appAddress, NodeId objectKey, Persistable object)
    {
	int replicaFactor;
	int i;
	LeafSet leafSet;
	Vector sortedleafSet;
	RMMessage msg;

	replicaFactor = ((Integer) (_appTable.get(appAddress))).intValue();
	leafSet = getLeafSet();
	if(!_objects.containsKey(objectKey)){
	    System.out.println("RM_UPDATE:: ERRRR... update called for object not in the system.\n");
	    return false;
	}
	sortedleafSet = (Vector)closestReplicas(leafSet, objectKey);
	sortedleafSet = compactReplicaSet(sortedleafSet, replicaFactor);


	/* Here we know that we have at most "replicaFactor" number of NodeId's 
	 * in the sortedLeafSet. So, now we should send "update" messages to
	 * these leafSet members.
	 */
	System.out.println("UPDATE::Number of replicas "+sortedleafSet.size());
	for(i=0; i < sortedleafSet.size(); i++){
	    msg = new RMMessage(getAddress(), object, objectKey, RMMessage.RM_UPDATE, replicaFactor, null);
	    //System.out.println("Replicating to "+((NodeId)sortedleafSet.elementAt(i)));
	    routeMsg((NodeId)sortedleafSet.elementAt(i), msg, getCredentials(), _sendOptions);
	}
	return true;


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
     * Generic class used to keep state associated  with an object which was  replicated 
     * using our Replica Manager. RM has a hashtable, having key as objectKey 
     * of the object and value as a object of class Entry. Currently, we just
     * use replicaFactor associated with the object as the state.
     */
    private class Entry{
	private int replicaFactor;
	
	public Entry(int replicaFactor){
	    this.replicaFactor = replicaFactor;
	}
	public int getreplicaFactor(){
	    return replicaFactor;
	}
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
	RMMessage insertmsg, deletemsg;
	Enumeration keys = _objects.keys();	
	StorageObject so;
	
	leafSet = getLeafSet();
	
	if( wasAdded){
	    /* A new node was added */ 

	    while(keys.hasMoreElements()){

		objectKey = (NodeId)keys.nextElement();
		replicaFactor =((Entry)_objects.get(objectKey)).getreplicaFactor();
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
			    so = retrieveStorageObject(objectKey);
			    insertmsg =  new RMMessage(getAddress(), so, objectKey, RMMessage.RM_INSERT, replicaFactor, so.getAuthorCredentials());

			    System.out.println("Replicating objectKey "+objectKey+" into node"+nh.getNodeId()+" from localNode"+getNodeId());
			    routeMsg(nh.getNodeId(), insertmsg, getCredentials(), _sendOptions);
			    if(replicas.size() >= (replicaFactor +1)){
				deletemsg =  new RMMessage(getAddress(), null, objectKey, RMMessage.RM_DELETE, replicaFactor, so.getAuthorCredentials());
				System.out.println("Deleting objectKey "+objectKey+"from replicaNode "+((NodeId)replicas.elementAt(replicaFactor))+" from localNodeId"+getNodeId());
				routeMsg((NodeId)(replicas.elementAt(replicaFactor)), deletemsg, getCredentials(), _sendOptions);
			    }
			}
		}
		else if(checkSecondaryLeader(replicas, getNodeId(), objectKey)){
		    if(!(nh.getNodeId()).equals(replicas.elementAt(0)))
			continue;
		     if(addedNodeExistsTwiceInUncompactedReplicaSet(uncompactedReplicas,nh.getNodeId()))
			continue;
		    System.out.println("I [" + getNodeId() + "] am the secondaryleader node");
		    
		    so =  retrieveStorageObject(objectKey);

		    insertmsg =  new RMMessage(getAddress(), so, objectKey, RMMessage.RM_INSERT, replicaFactor, so.getAuthorCredentials());
		    System.out.println("Replicating objectKey "+objectKey+" into node"+nh.getNodeId()+" from localNode "+getNodeId());
		    routeMsg(nh.getNodeId(), insertmsg, getCredentials(), _sendOptions);
		    
		    if(replicas.size() >= (replicaFactor +1)){
			deletemsg =  new RMMessage(getAddress(), null, objectKey, RMMessage.RM_DELETE, replicaFactor, so.getAuthorCredentials());
			System.out.println("Deleting objectKey "+objectKey+" from replicaNode "+((NodeId)replicas.elementAt(replicaFactor))+" at localNode "+getNodeId());
			routeMsg((NodeId)(replicas.elementAt(replicaFactor)), deletemsg, getCredentials(), _sendOptions);
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

			so = retrieveStorageObject(objectKey);
			insertmsg =  new RMMessage(getAddress(), so, objectKey, RMMessage.RM_INSERT, replicaFactor, so.getAuthorCredentials());
			System.out.println("Replicating objectKey "+objectKey+" into node"+((NodeId)(replicas.elementAt(replicaFactor -1)))+" at localNode "+getNodeId());
			routeMsg((NodeId)(replicas.elementAt(replicaFactor -1)), insertmsg, getCredentials(), _sendOptions);

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



    /** This function retrieves a object from the Storage Manager associated 
     * with the node. 
     * @param objectKey the pastry key associated with the object
     * @return StorageObject
     */
    private StorageObject retrieveStorageObject(NodeId objectKey){
	StorageManager sm;

	sm = getStorageManager();
	return sm.retrieve(objectKey);
	
    }

    /**
     * This function inserts the StorageObject using local Node's StorageManager.
     * It gets the authorCred from the StorageObject.
     * @param so the StorageObject to be stored
     * @param objectKey the pastry key associated with the object
     * @return true if object was successfully stored else false
     */
    private boolean insertStorageObject(StorageObject so, NodeId objectKey){
	Persistable original;
	Vector updates; 
	StorageManager sm;
	Credentials  authorCred;
	int i;

	sm = getStorageManager();
	original = so.getOriginal();
	updates = so.getUpdates();
	authorCred = so.getAuthorCredentials();

	if(!sm.store(objectKey, original, authorCred))
	    return false;
	
	for(i=0; i<updates.size(); i++){
	    if(!sm.update(objectKey, (Persistable)updates.elementAt(i)))
		return false;
	}
	return true;
    }    

    /**
     * This function inserts the Persistable object using local Node's StorageManager.
     * @param object the Persistable Object to be stored
     * @param objectKey the pastry key associated with the object
     * @return true if object was successfully stored else false
     */
    private boolean insertPersistableObject(Persistable object, NodeId objectKey, Credentials authorCred){
	StorageManager sm;
	
	sm = getStorageManager();
	if(!sm.store(objectKey, object, authorCred))
	    return false;
	return true;
    }


    /**
     * This function deletes the StorageObject using local Node's StorageManager.
     * It gets the authorCred from the StorageObject.
     * @param so the StorageObject to be deleted 
     * @param objectKey the pastry key associated with the object
     * @return true if object was successfully deleted else false
     */
    private boolean deleteStorageObject(StorageObject so, NodeId objectKey){
	StorageManager sm;
	Credentials  authorCred;

	sm = getStorageManager();
	authorCred = so.getAuthorCredentials();

	if(sm.delete(objectKey, authorCred))
	    return true;
	else
	    return false;
    }    

    /**
     * This function deletes the Persistable object using local Node's StorageManager.
     * @param objectKey the pastry key associated with the object
     * @param authorCred the credentials of the author
     * @return true if object was successfully deleted else false
     */
    private boolean deletePersistableObject(NodeId objectKey, Credentials authorCred){
	StorageManager sm;

	sm = getStorageManager();
	if(sm.delete(objectKey, authorCred))
	    return true;
	else 
	    return false;
    }

    /**
     * This function updates the Persistable object using local Node's StorageManager.
     * @param object the update object 
     * @param objectKey the pastry key associated with the object
     * @return true if object was successfully deleted else false
     */
    private boolean updatePersistableObject(Persistable object, NodeId objectKey){
	StorageManager sm;

	sm = getStorageManager();
	if(sm.update(objectKey, object))
	    return true;
	else
	    return false;

    }

}






