package rice.bscribe;

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

import rice.bscribe.messaging.*;
import rice.bscribe.testing.*;

/**
 * @(#) BScribe.java
 *
 * @version $Id$
 * @author Animesh Nandi
 * @author Atul Singh
 */
public class BScribe extends PastryAppl implements IScribeApp
{
    private BScribeTest m_bt;
    public Scribe m_scribe;
    private Credentials m_cred;
    public static final int NUM_CHANNELS = 16;
    public static final int NUM_SPARE_TREES = 1;
    private int m_inLink;
    private int m_outLink;
    public int m_load;
    public Vector m_spareIds ;
    public Vector m_channelIds;
    private SendOptions m_sendOptions;
    private Hashtable m_paths ;
    private Hashtable m_noDrop ;
    private int m_roundRobbinChildPos = 0;
    private static final int NUM_CHANNELS_base2 = 4;
    public int totalmsg = 0;
    public Hashtable message_count;

    private static class BScribeAddress implements Address {
	private int myCode = 0x8aec747c;
	
	public int hashCode() { return myCode; }

	public boolean equals(Object obj) {
	    return (obj instanceof BScribeAddress);
	}
    }

    private static Address m_address = new BScribeAddress ();

    public BScribe(BScribeTest bt, PastryNode pn, Scribe scribe, Credentials cred, int inLink, int outLink) {

	super(pn);
	m_bt = bt;
	m_scribe = scribe;
	m_cred = cred;
	m_inLink = inLink;
	m_outLink = outLink;
	m_spareIds = getSpareIds();
	m_channelIds = getChannelIds();
	m_load = 0;
	m_sendOptions = new SendOptions();
	m_paths = new Hashtable();
	m_noDrop = new Hashtable();
	message_count = new Hashtable();
	for(int i=0; i< m_channelIds.size() ; i++) {
	    m_noDrop.put((NodeId)m_channelIds.elementAt(i), new Vector());
	    m_paths.put((NodeId)m_channelIds.elementAt(i), new Vector());
	    message_count.put((NodeId)m_channelIds.elementAt(i), new Integer(0));
	}
    }

    
    public static Vector getChannelIds() {
	int i;
	NodeId topicId;
	Random rng = new Random();
	int j;
	Vector channelIds = new Vector();
	
	for(i=0; i< BScribe.NUM_CHANNELS; i++) {
	    topicId = NodeId.makeRandomId(rng);
	    topicId.setDigit((NodeId.nodeIdBitLength / BScribe.NUM_CHANNELS_base2) - 1 , i , BScribe.NUM_CHANNELS_base2);
	    for(j= 0 ; j < ((NodeId.nodeIdBitLength / BScribe.NUM_CHANNELS_base2) - 1); j++)
		topicId.setDigit(j, 0 , BScribe.NUM_CHANNELS_base2);
	    channelIds.add(topicId);
	}
	return channelIds;
    }

    
    public static Vector getSpareIds() {
	String tempString;
	int i;
	Credentials cred;
	NodeId topicId;
	Vector spareIds = new Vector();

	for(i=0 ; i<NUM_SPARE_TREES; i++) {
	    tempString = "spare" + i ;
	    topicId = generateTopicId(tempString);
	    spareIds.add(topicId);
	}
	return spareIds;
    }

   
    public void subscribeChannel(NodeId channelId) {
	Credentials cred;
	Topic topic;
	cred = m_cred;
	if(m_scribe.containsTopicId(channelId)){
	    topic  = m_scribe.getTopic(channelId);
	    topic.subscribe((IScribeApp)this);
	    System.out.println("I "+getNodeId()+" am already subscribed to this channel, so NewSubscriber should not be called ");
	}
	else
	    m_scribe.subscribe(channelId, this, cred);
    }



    public void subscribeSpare() {
	Credentials cred;
	int i;
	NodeId topicId;

	cred = m_cred;
	for(i=0; i< m_spareIds.size(); i++) {
	    topicId = (NodeId)m_spareIds.elementAt(i);
	    m_scribe.subscribe(topicId, this, cred);
	}
    }


    /**
     * up-call invoked by scribe when a publish message is 'delivered'.
     */
    public void receiveMessage( ScribeMessage msg ) {
	int value;
	if(!isSpareTopicId(msg.getTopicId())){
	    value = ((Integer)message_count.get(msg.getTopicId())).intValue();
	    value ++;
	    message_count.put(msg.getTopicId(), new Integer( value)); 
	    totalmsg ++;
	}
    }

    /**
     * up-call invoked by scribe when a publish message is forwarded through
     * the multicast tree.
     */
    public void forwardHandler( ScribeMessage msg ) {
	//System.out.println("Node:" + getNodeId() + " forwarding msg: "+ msg);
    }
    
    /**
     * up-call invoked by scribe when a node detects a failure from its parent.
     */
    public void faultHandler( ScribeMessage msg ) {
	//System.out.println("Node:" + getNodeId() +" handling fault: " + msg);
    }

    /**
     * up-call invoked by scribe when a node is added to the multicast tree.
     */
    public void subscribeHandler( ScribeMessage msg ) {
	System.out.println(" ------ subscribe handler invoked at" + getNodeId()+ "for child " + msg.getSource().getNodeId() + "----");
	NewSubscriber(msg.getSource(), msg.getTopicId());

    }


    public boolean enrouteMessage(Message msg, NodeId key, NodeId nextHop, SendOptions opt) {
	BScribeMsg bmsg;
	NodeHandle nh;
	Vector cleanList;
	Vector dirtyList;
	Vector listOfSiblings; 
	BScribeMsg newmsg;
	Topic topic;

	bmsg = (BScribeMsg)msg;
	switch(bmsg.type) {

	case BScribeMsg.REJOIN:

	    if(bmsg.lastHop != null) {
		if(getNodeId().equals(bmsg.lastHop)) {
		    //System.out.println("enrouteMsg:: lastHop was itself");
		    return true;
		}
	    }

	    System.out.println("enroutemsg:: Invoked at" + getNodeId() + "enrouteMsg.type=REJOIN");
	    System.out.println("enrouteMsg:: Node "+getNodeId()+" received REJOIN message for channel tree "+bmsg.channelId+" msg.Source "+bmsg.source.getNodeId()+" bmsg.spareId "+bmsg.spareId); 
	    if(!m_scribe.containsTopicId(bmsg.spareId)) {
		System.out.println("enrouteMsg:: Node "+getNodeId()+"  receiving request to add a child who is trying to REJOIN, is not part of Spare Capacity tree");
		return true;
	    }

	    cleanList = new Vector();
	    dirtyList = new Vector();
	    nh = FindNextChild(cleanList, dirtyList, bmsg);

	    if(nh != null) {
		System.out.println("enrouteMsg:: FindNextChild returned "+nh.getNodeId());
		listOfSiblings = bmsg.listOfSiblings;
		UnionOfVectors(listOfSiblings, dirtyList, cleanList);
		System.out.println("printing dirty list");
		printListOfSiblings(dirtyList);
		System.out.println("printing clean list");
		printListOfSiblings(cleanList);
		System.out.println("printing union set");
		printListOfSiblings(listOfSiblings);
		System.out.println("enrouteMsg:: REJOIN request is sent down the spare tree\n");
		bmsg.lastHop = getNodeId();
		routeMsgDirect(nh ,bmsg, getCredentials(), m_sendOptions);
		return false;
	    }
	    else 
		{

		    if((m_load < m_outLink) && !bmsg.source.getNodeId().equals(getNodeId()) && getChannelTopicIds().contains(bmsg.channelId)) {
			System.out.println("enrouteMsg:: This node could potentially satisfy REJOIN request\n");
			return CheckJoining(bmsg);
		    }
		    else
			{
			    
			    if(bmsg.source.getNodeId().equals(getNodeId()))
				System.out.println("enroutemsg:: Node was not allowed to REJOIN to himself");

			    if( m_load >= m_outLink) {
				System.out.println("enrouteMsg:: "+getNodeId()+" Unsubscribing from spare capacity tree\n");
				//m_scribe.unsubscribe(bmsg.spareId, this, m_cred);
			    }

			    if(!getChannelTopicIds().contains(bmsg.channelId))
				System.out.println("enroutemsg:: This node is in the spare tree but has not subscribed to the required channel yet");
			    
			    if(!bmsg.traversed.contains(getNodeId()))
				bmsg.traversed.add(getNodeId());
			    if(bmsg.listOfSiblings.size()!=0) {
				nh = (NodeHandle)bmsg.listOfSiblings.remove(bmsg.listOfSiblings.size()-1);
				bmsg.lastHop = getNodeId();
				routeMsgDirect(nh ,bmsg, getCredentials(), m_sendOptions);
				return false;
			    }
			    else
				{
				    topic  = m_scribe.getTopic(bmsg.spareId);
				    nh = topic.getParent();
				    if(nh==null) {
					if(!isRoot(bmsg.spareId))
					    return true;
					else
					    {
						finish(bmsg);
					    }
				    }
				    bmsg.lastHop = getNodeId();
				    System.out.println("enrouteMsg::Sending to parent, size of traversed "+bmsg.traversed.size());
				    routeMsgDirect(nh ,bmsg, getCredentials(), m_sendOptions);
				    return false;
				}
			}
		}
	    

	default:
	    break;
	}
	return true;
    }



    private boolean CheckJoining(BScribeMsg bmsg) {
	NodeId subscriber;
	NodeId topicId;
	NodeHandle child;
	BScribeMsg newmsg;
	NodeHandle nh;
	Topic topic;
	
	subscriber = bmsg.source.getNodeId();
	topicId = bmsg.channelId;
	if(LoopDetected(subscriber, topicId)) {
	    System.out.println("checkjoining:: Loop was detected at" + getNodeId());
	    if(!bmsg.traversed.contains(getNodeId()))
		bmsg.traversed.add(getNodeId());
	    if(bmsg.listOfSiblings.size()!=0) {
		nh = (NodeHandle)bmsg.listOfSiblings.remove(bmsg.listOfSiblings.size()-1);
		bmsg.lastHop = getNodeId();
		routeMsgDirect(nh ,bmsg, getCredentials(), m_sendOptions);
		return false;
	    }
	    else
		{
		   
		    topic = m_scribe.getTopic(bmsg.spareId);
		    nh = topic.getParent();
		    if(nh==null) {
			if(!isRoot(bmsg.spareId))
			    return true;
			else
			    {
				finish(bmsg);
			    }
		     }
		    bmsg.lastHop = getNodeId();
		    System.out.println("Checkjoining::Sending to parent, size of traversed "+bmsg.traversed.size());
		    routeMsgDirect(nh ,bmsg, getCredentials(), m_sendOptions);
		    return false;
		}
	}
	else
	    {
		child = bmsg.source;
		topic = m_scribe.getTopic(bmsg.channelId);
		topic.addChild(child);
		m_load ++;
		System.out.println("CheckJoining:: Request was satisfied");
		if( DirtyLink(subscriber, bmsg.channelId, getNodeId()) || !PathEmpty(bmsg.channelId)) {
		    
		    Vector localPath = getPath(bmsg.channelId);
		    Vector propogatePath = (Vector)localPath.clone();
		    propogatePath.add(getNodeId());
		    System.out.println("Checkjoining:: " + getNodeId() + " Sending POSITIVEPATHUPDATE to" + bmsg.source.getNodeId());
		    newmsg = new BScribeMsg(getAddress(), BScribeMsg.POSITIVEPATHUPDATE, topicId, propogatePath);
		    routeMsgDirect(bmsg.source, newmsg, getCredentials(), m_sendOptions);
		}
		return false;
	    }
    }

    public boolean isRoot(NodeId topicId) {
	LeafSet leafset;
	NodeHandle left;
	NodeHandle right;
	NodeId.Distance lDist;
	NodeId.Distance rDist;
	NodeId.Distance sDist;

	leafset = getLeafSet();
	left = leafset.get(-1);
	right = leafset.get(1);
	if(left == null && right==null)
	    return true;
	sDist = getNodeId().distance(topicId);
	lDist = left.getNodeId().distance(topicId);
	rDist = right.getNodeId().distance(topicId);
	if((sDist.compareTo(lDist) < 0) && (sDist.compareTo(rDist) < 0 ))
	    return true;
	else
	    return false;
    }


    private void UnionOfVectors(Vector listOfSiblings, Vector dirtyList, Vector cleanList) {
	NodeHandle nh;
	int i;

	for(i=0 ; i< dirtyList.size(); i++ ) {
	    nh = (NodeHandle)dirtyList.elementAt(i);
	    if(listOfSiblings.contains(nh))
		listOfSiblings.remove(nh);
	    listOfSiblings.add(nh);
	}
	for(i=0 ; i< cleanList.size(); i++ ) {
	    nh = (NodeHandle)cleanList.elementAt(i);
	    if(listOfSiblings.contains(nh))
		listOfSiblings.remove(nh);
	    listOfSiblings.add(nh);
	}
    }
 

    public void messageForAppl(Message msg) {
	BScribeMsg bmsg;
	BScribeMsg newmsg;
	NodeId spareId;
	NodeId channelId;
	Vector localPath;
	Vector propogatePath;
	Topic channelTopic;
	Vector children = new Vector();
	Set childrenSet;
	Iterator it;
	NodeHandle child;
	Vector children_clone;
	int i;
	NodeHandle nh;
	Vector cleanList;
	Vector dirtyList;
	Vector listOfSiblings; 

	bmsg = (BScribeMsg)msg;
	switch(bmsg.type) {

	case BScribeMsg.REJOIN :
	    System.out.println("messageforAppl:: Invoked at" + getNodeId() + "messageforappl.type=REJOIN");
	    cleanList = new Vector();
	    dirtyList = new Vector();
	    nh = FindNextChild(cleanList, dirtyList, bmsg);
	    
	    if(nh != null) {
		System.out.println("messageforapppl:: FindNextChild returned "+nh.getNodeId());
		listOfSiblings = bmsg.listOfSiblings;
		UnionOfVectors(listOfSiblings, dirtyList, cleanList);
		System.out.println("printing dirty list");
		printListOfSiblings(dirtyList);
		System.out.println("printing clean list");
		printListOfSiblings(cleanList);
		System.out.println("printing union set");
		printListOfSiblings(listOfSiblings);
		System.out.println("messageforappl:: REJOIN request is sent down the spare tree\n");
		bmsg.lastHop = getNodeId();
		routeMsgDirect(nh ,bmsg, getCredentials(), m_sendOptions);
	    }
	    else
		System.out.println("messageforappl:: This should not happen");
	    break;

	case BScribeMsg.DROPPED :
	    System.out.println("messageforAppl:: Invoked at" + getNodeId() +"messageforappl.type=DROPPED");
	    // We first need to send negative pathupdate information down its subtree
	    localPath = getPath(bmsg.channelId);
	    localPath.removeAllElements();
	    channelId = bmsg.channelId;
	    channelTopic = m_scribe.getTopic(channelId);
	    childrenSet = channelTopic.getChildren();

	    if (!childrenSet.isEmpty()) {
		children_clone = new Vector();
		it = childrenSet.iterator();
		while(it.hasNext()) {
		    child = (NodeHandle)it.next();
		    children_clone.add(child);
		}
		for(i=0; i < children_clone.size(); i++) {
		    child = (NodeHandle)children_clone.elementAt(i);
		    propogatePath = (Vector)localPath.clone();
		    if(DirtyLink(child.getNodeId(), channelId, getNodeId()))
			propogatePath.add(getNodeId());
		    System.out.println("messageForAppl:: " + getNodeId() + " Sending NEGATIVEPATHUPDATE to a child" + child.getNodeId());
		    newmsg = new BScribeMsg(getAddress(), BScribeMsg.NEGATIVEPATHUPDATE, channelId, propogatePath);
		    newmsg.lastHop = getNodeId();
		    routeMsgDirect(child, newmsg, getCredentials(), m_sendOptions);
		
		}
	    }
	    // We then need to rejoin via the spare capacity tree
	    channelId = bmsg.channelId;
	    spareId = (NodeId)m_spareIds.elementAt(0);
	    newmsg = new BScribeMsg(getAddress(), BScribeMsg.REJOIN, channelId, spareId, thePastryNode.getLocalHandle(), getNodeId());
	    System.out.println("Node" + getNodeId() + " tries to REJOIN channel tree with channel Id " + channelId + " via the spare capacity tree whose topic is "+spareId);
	    routeMsg(spareId ,newmsg, getCredentials(), m_sendOptions);
	    break;


	case BScribeMsg.TAKECHILDNEGATIVEPATHUPDATE :
	    System.out.println("messageforAppl:: Invoked at" + getNodeId() +"messageforappl.type=TAKECHILDNEGATIVEPATHUPDATE");
	    // We ONLY  need to send negative pathupdate information down its subtree
	    localPath = getPath(bmsg.channelId);
	    localPath.removeAllElements();
	    channelId = bmsg.channelId;
	    channelTopic = m_scribe.getTopic(channelId);
	    childrenSet = channelTopic.getChildren();

	    if (!childrenSet.isEmpty()) {
		children_clone = new Vector();
		it = childrenSet.iterator();
		while(it.hasNext()) {
		    child = (NodeHandle)it.next();
		    children_clone.add(child);
		}
		for(i=0; i < children_clone.size(); i++) {
		    child = (NodeHandle)children_clone.elementAt(i);
		    propogatePath = (Vector)localPath.clone();
		    if(DirtyLink(child.getNodeId(), channelId, getNodeId()))
			propogatePath.add(getNodeId());
		    System.out.println("messageForAppl:: " + getNodeId() + " Sending NEGATIVEPATHUPDATE to a child" + child.getNodeId());
		    newmsg = new BScribeMsg(getAddress(), BScribeMsg.NEGATIVEPATHUPDATE, channelId, propogatePath);
		    newmsg.lastHop = getNodeId();
		    routeMsgDirect(child, newmsg, getCredentials(), m_sendOptions);
		
		}
	    }
	    break;

	case BScribeMsg.TAKECHILD :
	    System.out.println("messageforAppl:: Invoked at" + getNodeId() +"messageforappl.type=TAKECHILD");
	    getNoDrop(bmsg.channelId).add((bmsg.childToAdopt).getNodeId());
	    System.out.println("NoDrop at" + getNodeId() + "for channel tree=" + bmsg.channelId );
	    for(i=0; i< getNoDrop(bmsg.channelId).size(); i++) 
		System.out.print((NodeId)getNoDrop(bmsg.channelId).elementAt(i));
	    System.out.println("");
	    channelTopic = m_scribe.getTopic(bmsg.channelId);
	    channelTopic.addChild(bmsg.childToAdopt);
	    System.out.println("messageforappl::Invoking Newsubscriber from TAKECHILD"); 
	    NewSubscriber(bmsg.childToAdopt, bmsg.channelId);
	    break;



	case BScribeMsg.POSITIVEPATHUPDATE :
	    System.out.println("messageforAppl:: Invoked at" + getNodeId() +"messageforappl.type=POSITIVEPATHUPDATE");
	    BScribePositivePathUpdate(bmsg);
	    break;

	case BScribeMsg.NEGATIVEPATHUPDATE :
	    System.out.println("messageforAppl:: Invoked at" + getNodeId() +"messageforappl.type=NEGATIVEPATHUPDATE");
	    BScribeNegativePathUpdate(bmsg);
	    break;

	default:
	    break;
	}
    }


    private void BScribePositivePathUpdate(BScribeMsg bmsg) {
	BScribeMsg newmsg;
	NodeId channelId;
	Vector localPath;
	Vector propogatePath;
	Topic channelTopic;
	Set childrenSet;
	Iterator it;
	NodeId spareTopicId;
	NodeHandle child;
	Vector children_clone;
	int i;

	System.out.println("PostivePathUpdate ::size of path received is " + bmsg.path.size());
	channelId = bmsg.channelId;
	localPath = getPath(channelId);
	UpdatePath(localPath,bmsg.path);
	channelTopic = m_scribe.getTopic(channelId);
	childrenSet = channelTopic.getChildren();

	if (!childrenSet.isEmpty()) {
	    children_clone = new Vector();
	    it = childrenSet.iterator();
	    while(it.hasNext()) {
		child = (NodeHandle)it.next();
		children_clone.add(child);
	    }
	    for(i=0; i < children_clone.size(); i++) {
		child = (NodeHandle)children_clone.elementAt(i);
		if(LoopDetected(child.getNodeId(), channelId)) {
		    //m_bt.BFS(channelId);
		    channelTopic.removeChild(child);
		    //getNoDrop(channelId).remove(child.getNodeId());
		    m_load --;
		    System.out.println("PositivePathUpdate:: " + getNodeId() + " sending a DROPPED message to" + child.getNodeId() + " because of loop detection"); 
		    newmsg = new BScribeMsg(getAddress(), BScribeMsg.DROPPED, channelId);
		    routeMsgDirect(child, newmsg, getCredentials(), m_sendOptions);
		}
		else {
		    propogatePath = (Vector)localPath.clone();
		    propogatePath.add(getNodeId());
		    System.out.println("PositivePathUpdate:: " +  getNodeId() + " Sending POSITIVEPATHUPDATE to" + child.getNodeId());
		    newmsg = new BScribeMsg(getAddress(), BScribeMsg.POSITIVEPATHUPDATE, channelId, propogatePath);
		    routeMsgDirect(child, newmsg, getCredentials(), m_sendOptions);
		}
	    }
	}
	if(m_load < m_outLink ) {
	    spareTopicId = (NodeId)getSpareIds().elementAt(0);
	    if(!m_scribe.containsTopicId(spareTopicId))
		m_scribe.subscribe(spareTopicId, this, m_cred);
	}
        
    }

    private void BScribeNegativePathUpdate(BScribeMsg bmsg) {
	BScribeMsg newmsg;
	NodeId channelId;
	Vector localPath;
	Vector propogatePath;
	Topic channelTopic;
	Set childrenSet;
	Iterator it;
	NodeHandle child;
	Vector children_clone;
	int i;

	System.out.println("NegativePathUpdate ::size of path received is " + bmsg.path.size());
	channelId = bmsg.channelId;
	localPath = getPath(channelId);
	UpdatePath(localPath, bmsg.path);
	channelTopic = m_scribe.getTopic(channelId);
	childrenSet = channelTopic.getChildren();

	if (!childrenSet.isEmpty()) {
	    children_clone = new Vector();
	    it = childrenSet.iterator();
	    while(it.hasNext()) {
		child = (NodeHandle)it.next();
		children_clone.add(child);
	    }
	    for(i=0; i < children_clone.size(); i++) {
		child = (NodeHandle)children_clone.elementAt(i);
		propogatePath = (Vector)localPath.clone();
		if(!PathEmpty(channelId) || DirtyLink(child.getNodeId(), channelId, getNodeId()))
		    propogatePath.add(getNodeId());
		System.out.println("NegativePathUpdate::" + getNodeId() + " Sending NEGATIVEPATHUPDATE to" + child.getNodeId());
		newmsg = new BScribeMsg(getAddress(), BScribeMsg.NEGATIVEPATHUPDATE, channelId, propogatePath);
		routeMsgDirect(child, newmsg, getCredentials(), m_sendOptions);
	    }
	}

    }
    
   

    private void NewSubscriber(NodeHandle nh, NodeId p_topicId) {
	int index;
	Vector channelTopicIds ;
	NodeId topicId;
	Topic topic;
	Set children;
	Iterator it;
	NodeHandle child;
	boolean newNodeDropped = false;
	Vector commonPrefixTopicIds = new Vector();
	NodeId rootTopicId = null;
	NodeHandle victimChild ;
	NodeHandle minChild ;
	BScribeMsg newmsg;
	int i;
	Vector sortedChildren = null;
	NodeHandle takeChildParent = null;
	Random rng = new Random();
	NodeId victimTopicId = null;
	int pos;

	if(isSpareTopicId(p_topicId)) {
	    System.out.println("NewSubscriber:: child added was for spare tree");
	    return;
	}
	m_load ++;
	System.out.println("NewSubscriber:: current load: "+m_load);
	if(m_load == m_outLink) {
	    for(i=0 ; i<NUM_SPARE_TREES; i++) {
		topicId = (NodeId)m_spareIds.elementAt(i);
		//m_scribe.unsubscribe(topicId, this, m_cred);
		System.out.println("NewSubscriber:: "+getNodeId()+" unsubscribing from spare capacity tree\n");
	    }
	}

	if(m_load > m_outLink) {
	    System.out.println("NewSubscriber:: we need to drop some child since load has increased\n");
	    channelTopicIds = getChannelTopicIds();
	    for(index=0; index < channelTopicIds.size() ; index ++) {
		topicId = (NodeId) channelTopicIds.elementAt(index);
		if(findCommonDigit(getNodeId(),topicId) != 0) {
		    commonPrefixTopicIds.add(topicId);
		    continue;
		}
		if(isRoot(topicId)) {
		    if(!commonPrefixTopicIds.contains(topicId))
			commonPrefixTopicIds.add(topicId);
		    continue;
		}
		topic = m_scribe.getTopic(topicId);
		children = topic.getChildren();

		if (children.isEmpty())
		    continue;
		it = children.iterator();
		child = (NodeHandle)it.next();
		//m_bt.BFS(topicId);
		topic.removeChild(child);
		//getNoDrop(topicId).remove(child.getNodeId());
		m_load --;
		if(child.getNodeId().equals(nh.getNodeId()))
		    newNodeDropped = true;
		newmsg = new BScribeMsg(getAddress(), BScribeMsg.DROPPED, topicId);
		routeMsgDirect(child, newmsg, getCredentials(), m_sendOptions);
		System.out.println("NewSubscriber:: Dropping the child" + child.getNodeId() + " corresponding to its subtree" + topicId + " in which it should be a leaf\n");
		break;
	    }
	    if(m_load > m_outLink) {
		Reorder(commonPrefixTopicIds);
		victimChild = null;
		minChild = null;
		for(index = 0; index < commonPrefixTopicIds.size() ; index ++) {
		    topicId = (NodeId) commonPrefixTopicIds.elementAt(index);
		    topic = m_scribe.getTopic(topicId);
		    children = topic.getChildren();

		    if (children.isEmpty()) {
			continue;
		    }
		    it = children.iterator();
		    sortedChildren = SortChildren(it, topicId);
		    if(minChild == null) {
			minChild = (NodeHandle)sortedChildren.elementAt(0);
			victimTopicId = topicId;
		    }
		    for(i=0; i< sortedChildren.size(); i++) {
			child = (NodeHandle)sortedChildren.elementAt(i);
			if(!getNoDrop(topicId).contains(child.getNodeId())) {
			    victimChild = child;
			    victimTopicId = topicId;
			    System.out.println("victimChild=" + victimChild.getNodeId() + " for victimchannel" + victimTopicId);
			    
			    break;
			}
		    }
		    if(victimChild != null)
			break;
		}
		if(victimChild != null) {
		    System.out.println("Newsubscriber:: Now we need to look for a random child who can take this child");
		    takeChildParent = null;
		    //The takechildparent node cannot be the node that was dropped - so we remove that node
		    sortedChildren.remove(victimChild);
		    for(i=0; i< sortedChildren.size() ; i++) {
			child = (NodeHandle)sortedChildren.elementAt(i);
			if(findCommonPrefix(child.getNodeId(), victimTopicId) > 0)  
			    break;
		    }
		    if(i < sortedChildren.size()) {
			pos = i + rng.nextInt(sortedChildren.size() - i);
			takeChildParent = (NodeHandle)sortedChildren.elementAt(pos);
			System.out.println("NewSubscriber:: takeChildParent=" + takeChildParent.getNodeId());
			if(!getNoDrop(victimTopicId).contains(takeChildParent.getNodeId()))
			   getNoDrop(victimTopicId).add(takeChildParent.getNodeId());
			System.out.println("NoDrop at" + getNodeId() +"for channel tree=" + victimTopicId);
			for(i=0; i< getNoDrop(victimTopicId).size(); i++) 
			    System.out.print((NodeId)getNoDrop(victimTopicId).elementAt(i));
			System.out.println("");
		    }
		}
		if((victimChild != null) && (takeChildParent != null)) {
		    System.out.println("NewSubscriber::Connect the dropped child using TAKECHILD");
		    //m_bt.BFS(victimTopicId);
		    topic = m_scribe.getTopic(victimTopicId);
		    topic.removeChild(victimChild);
		    m_load --;
		    if(victimChild.getNodeId().equals(nh.getNodeId()))
			newNodeDropped = true;
		    System.out.println("NewSubscriber::" + getNodeId() + " Sending a TAKECHILD message to " + takeChildParent.getNodeId() + " to include child " + victimChild.getNodeId());  
		    newmsg = new BScribeMsg(getAddress(), BScribeMsg.TAKECHILD, victimTopicId, victimChild );
		    routeMsgDirect(takeChildParent, newmsg, getCredentials(), m_sendOptions);


		    System.out.println("NewSubscriber::" + getNodeId() + "Sending a TAKECHILDNEGATIVEPATHUPDATE message to " + victimChild.getNodeId());
		    newmsg = new BScribeMsg(getAddress(), BScribeMsg.TAKECHILDNEGATIVEPATHUPDATE, victimTopicId);
		    routeMsgDirect(victimChild, newmsg, getCredentials(), m_sendOptions);
		    
		}
		else
		    {
			if(victimChild == null) {
			    victimChild = minChild;
			    System.out.println("Newsubscriber:: victimChild chosen was in noDrop");
			}
			System.out.println("NewSubscriber::Connect the dropped child via the spare tree");
			//m_bt.BFS(victimTopicId);
			topic = m_scribe.getTopic(victimTopicId);
			topic.removeChild(victimChild);
			//getNoDrop(victimTopicId).remove(victimChild.getNodeId());
			m_load --;
			if(victimChild.getNodeId().equals(nh.getNodeId()))
			    newNodeDropped = true;
			System.out.println("Newsubscriber:: " + getNodeId() + "sending DROPPED message to " + victimChild.getNodeId());
			newmsg = new BScribeMsg(getAddress(), BScribeMsg.DROPPED, victimTopicId);
			routeMsgDirect(victimChild, newmsg, getCredentials(), m_sendOptions);
		    }
			
	    }
		
	}
    
	
	boolean pathEmptyResult = PathEmpty(p_topicId);
	boolean dirtyLinkResult = DirtyLink(nh.getNodeId(), p_topicId, getNodeId());
	
	System.out.println("newNodeDropped=" + newNodeDropped);
	if(!pathEmptyResult)
	    System.out.println("pathEmptyResult=" + pathEmptyResult);
	if(dirtyLinkResult) 
	    System.out.println("dirtyLinkResult=" + dirtyLinkResult);

	if(!newNodeDropped && (!pathEmptyResult || dirtyLinkResult))
	    {
		System.out.println("NewSubscriber:: We need to propogate Positive path update to the child that was just added");
		Vector localPath = getPath(p_topicId);
		Vector propogatePath = (Vector)localPath.clone();
		propogatePath.add(getNodeId());
		System.out.println("Newsubscriber:: " + getNodeId() + "sending a POSITIVEPATHUPDATE message to" + nh.getNodeId());
		newmsg = new BScribeMsg(getAddress(), BScribeMsg.POSITIVEPATHUPDATE, p_topicId, propogatePath);
		routeMsgDirect(nh, newmsg, getCredentials(), m_sendOptions);
	    }
	
    }
    
    
    private NodeHandle FindNextChild(Vector cleanList, Vector dirtyList, BScribeMsg bmsg) {
	NodeId spareTopicId;
	Topic spareTopic;
	NodeHandle child;
	Vector children = new Vector();
	Set childrenSet;
	int i;
	Iterator it;

	
        spareTopicId = bmsg.spareId;
	spareTopic = m_scribe.getTopic(spareTopicId);
	childrenSet = spareTopic.getChildren();

	if (childrenSet.isEmpty())
	    return null;
	it = childrenSet.iterator();
	while(it.hasNext()) {
	    child = (NodeHandle)it.next();
	    children.add(child);
	}
	if (m_roundRobbinChildPos >= children.size())
	    m_roundRobbinChildPos = 0; 
	for (i = 1; i <= children.size(); i++) {
	    m_roundRobbinChildPos = (m_roundRobbinChildPos + 1) % children.size() ;
	    child = (NodeHandle) children.elementAt(m_roundRobbinChildPos);
	    if(EligibleChild(child, bmsg)) {
		if(DirtyLink(bmsg.source.getNodeId(), bmsg.channelId, child.getNodeId()))
		    dirtyList.add(child);
		else
		    cleanList.add(child);
	    }
	}
	if(cleanList.size()!=0)
	    return (NodeHandle)cleanList.remove(cleanList.size()-1);
	else if(dirtyList.size()!=0)
	    return (NodeHandle)dirtyList.remove(dirtyList.size()-1);
	else
	    return null;
    }



    private boolean EligibleChild(NodeHandle child, BScribeMsg bmsg) {
	if(bmsg.traversed.size() ==0)
	    return true;
	return !bmsg.traversed.contains(child.getNodeId());
    }


   
    
    private boolean DirtyLink(NodeId childId, NodeId topicId, NodeId parentId) {
	NodeId.Distance distChild;
        NodeId.Distance distParent;
	int child_prefix = 0;
	int parent_prefix = 0;


	child_prefix = findCommonPrefix(childId, topicId);
	parent_prefix = findCommonPrefix(parentId, topicId);

	if(child_prefix > parent_prefix)
	    return true;
	else if(child_prefix == parent_prefix) {
	    distChild = topicId.distance(childId);
	    distParent = topicId.distance(parentId);
	    if (distChild.compareTo(distParent) < 0)
		return true;
	    else 
		return false;
	}
	else 
	    return false;
    }




    private boolean PathEmpty(NodeId topicId) {
	if (getPath(topicId).size() == 0) 
	    return true;
	else
	    return false;
    }
    
    private boolean LoopDetected(NodeId subId, NodeId channelId) {
	Vector localPath;
	boolean result;
	int i;
	NodeId nodeId;
	
        localPath = getPath(channelId);
	result = localPath.contains(subId);
	if(result) {
	    System.out.print("LocalPath is:");
	    for(i=0; i< localPath.size(); i++) {
		nodeId = (NodeId) localPath.elementAt(i);
		System.out.print(nodeId);
	    }
	    System.out.println("");
	}
	return result;
    }
    


   

     private Vector SortChildren(Iterator it, NodeId topicId) {
	int minV;
	int v;
	NodeHandle minChild;
	NodeHandle child;
	Vector children_clone;
	int i;
	Vector sortedChildren = new Vector();

	children_clone = new Vector();
	while(it.hasNext()) {
	    child = (NodeHandle)it.next();
	    children_clone.add(child);
	}

	while(children_clone.size() > 0) {
	    minChild = (NodeHandle)children_clone.elementAt(0);
	    minV = findCommonPrefix(minChild.getNodeId() ,topicId);
	    for(i=1; i < children_clone.size(); i++) {
		child = (NodeHandle)children_clone.elementAt(i);
		v = findCommonPrefix(child.getNodeId() ,topicId);
		if(v < minV) {
		    minV = v;
		    minChild = child;
		}
	    }
	    sortedChildren.add(minChild);
	    children_clone.remove(minChild);
	}
	System.out.println("Sorted Children for the channel" + topicId);
	for(i=0; i < sortedChildren.size(); i++)
	    System.out.print(((NodeHandle)sortedChildren.elementAt(i)).getNodeId());
	System.out.println("");
	return sortedChildren;
    }
    


    private boolean isSpareTopicId(NodeId topicId) {
	return m_spareIds.contains(topicId);
    }


    private Vector getChannelTopicIds() {
	Vector topicIds;
	int index = 0;
	NodeId topicId;

        topicIds = m_scribe.getTopicIds();
	while(index < topicIds.size()) {
	    topicId = (NodeId) topicIds.elementAt(index);
	    if( isSpareTopicId(topicId))
		topicIds.removeElementAt(index);
	    else
		index ++;
	}
	return topicIds;
    }

     private Vector getSpareTopicIds() {
	Vector topicIds;
	int index = 0;
	NodeId topicId;

        topicIds = m_scribe.getTopicIds();
	while(index < topicIds.size()) {
	    topicId = (NodeId) topicIds.elementAt(index);
	    if( !(isSpareTopicId(topicId)))
		topicIds.removeElementAt(index);
	    else
		index ++;
	}
	return topicIds;
    }
    

    private void UpdatePath(Vector localPath, Vector msgPath) {
	int i;

	localPath.removeAllElements();
	for(i=0; i< msgPath.size(); i++) 
	    localPath.add(msgPath.elementAt(i));
   
    }

    public Credentials getCredentials(){
	return m_cred;
    }

    public Address getAddress() {
	return m_address;
    }

    private int findCommonPrefix(NodeId nodeId1, NodeId nodeId2) {
	int result;

	result = (NodeId.nodeIdBitLength - 1 ) -  nodeId1.indexOfMSDB(nodeId2);
	return result;
    }



    private int findCommonDigit(NodeId nodeId1, NodeId nodeId2) {
	int result;

	result = (((NodeId.nodeIdBitLength/NUM_CHANNELS_base2) - 1 ) -  nodeId1.indexOfMSDD(nodeId2, NUM_CHANNELS_base2));
	return result;
    }


    public void create( NodeId topicId ) {
	m_scribe.create( topicId, m_cred);
    }

    public void publish( NodeId topicId ) {
	m_scribe.publish( topicId, null, m_cred);
    }

    private Vector getPath(NodeId channelId) {
	Set keySet;
	Iterator it;
	NodeId key;

	keySet = m_paths.keySet();
	it = keySet.iterator();
	while(it.hasNext()) {
	    key = (NodeId)it.next();
	    if (key.equals(channelId))
		return (Vector)m_paths.get(key);
	    }
	return null;
    }

    private Vector getNoDrop(NodeId channelId) {
	Set keySet;
	Iterator it;
	NodeId key;

	keySet = m_noDrop.keySet();
	it = keySet.iterator();
	while(it.hasNext()) {
	    key = (NodeId)it.next();
	    if (key.equals(channelId))
		return (Vector)m_noDrop.get(key);
	    }
	return null;
    }

    private void Reorder(Vector list) {
	NodeId topicId;
	int i;
	int pos=0;

	for(i=0; i<list.size(); i++) {
	    topicId = (NodeId)list.elementAt(pos);
	    if(isRoot(topicId)) {
		list.remove(topicId);
		list.add(topicId);
	    }
	    else
		pos ++;
	}

    }
    
    public static NodeId generateTopicId( String topicName ) { 
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

    public void printListOfSiblings(Vector list) {
	int i;
	int size;

	size = list.size();
	for(i=0; i< size; i++) 
	    System.out.println(((NodeHandle)list.elementAt(i)).getNodeId());
	System.out.println("***********");
    }
    
    public void printNodeIdList(Vector list) {
	int i;
	int size;

	size = list.size();
	for(i=0; i< size; i++) 
	    System.out.println((NodeId)list.elementAt(i));
	System.out.println("***********");
    }

    public void finish(BScribeMsg bmsg) {
	int i;
	BScribe app;
	NodeId channelId;
	NodeId spareId;
	
	channelId = bmsg.channelId;
	spareId = bmsg.spareId;
	
	System.out.println("It is the root");
	System.out.println("Printing the traversed list of the spare tree");
	printNodeIdList(bmsg.traversed);
	System.out.println("Printing the spare tree");
	m_bt.BFS(spareId);

	System.out.println("Printing the channeltree which the node wanted to join");
	m_bt.BFS(channelId);
	for(i=0 ; i< m_bt.bscribeClients.size(); i++) {
	    app = (BScribe)m_bt.bscribeClients.elementAt(i);
	    System.out.println("BScribeApp[" + i + "]:totalmsg= " + app.totalmsg + ", load= " + app.m_load +" NodeId "+app.getNodeId());

	}
    }

}















