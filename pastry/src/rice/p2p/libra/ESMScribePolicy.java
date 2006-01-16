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
package rice.p2p.libra;

import java.util.*;
import java.lang.Double;
import rice.p2p.commonapi.*;
import rice.p2p.scribe.*;
import rice.p2p.scribe.messaging.*;
import rice.environment.logging.Logger;

/**
 * This class represents ESMLibra's policy for Scribe,
 */
public class ESMScribePolicy implements ScribePolicy {

    /*** Policies based on TopicNames 
	 
    *** The first set will be used to evaluate basic Libra ( In these dummy groups the load updates are made to ensure that predicateVerified() is true, i.e they have bandwidth, low loss rate, ******
    Topic 0 - 9  : NAIVE SCRIBE
    Topic 10 - 19 : LIBRA ( Biasing locality only)     
    Topic 20 - 29 : CENTRALIZED - RANDOM
    Topic 30 - 39 : CENTRALIZED - OPTIMAL LOCALITY
    Topic 40 - 49 : default
    
    ***** The second set will be used in the context of ESM
    Topic 50 - 59 : NAIVE Strawman SCRIBE
    Topic 60 - 69 : CENTRALIZED - RANDOM
    Topic 70 - 79 : LIBRA ( Biasing locality, depth)
    Topic 80 - 89 : CENTRALIZED - OPTIMAL ( Biasing locality, depth)
    

    *****/

    public static final int NAIVESCRIBE = 1;
    public static final int LIBRA = 2;
    public static final int CENTRALRANDOM = 3;
    public static final int CENTRALOPTIMAL = 4; // w.r.t to locality
    public static final int DEFAULT = NAIVESCRIBE;

    public static final int ESMNAIVESCRIBE = 6;
    public static final int ESMLIBRA = 7;
    public static final int ESMCENTRALRANDOM = 8;
    public static final int ESMCENTRALOPTIMAL = 9; // w.r.t to locality
    public static final int ESMDEFAULT = ESMNAIVESCRIBE;
    
    
    // These policies determine the preferable order of children traversal in anycast algorithm
    public static final int ESMLIBRA_RANDOM = 11;
    public static final int ESMLIBRA_LOCALITY = 12;
    public static final int ESMLIBRA_BANDWIDTH = 13;
    public static final int ESMLIBRA_DEPTH = 14;
    public static final int ESMLIBRA_TIME = 15;  // biasing towards members with higher remaining times 
    public static final int ESMLIBRA_COMBINED = 16;
    
    
    

  /**
   * A reference to this policy's splitstream object
   */
  protected MyLibraClient myLibraClient;

  /**
   * A reference to this policy's scribe object
   */
  protected Scribe scribe;

    /**
     * A radom number used to introduce randomness while adding children.
     */
    Random rng = new Random();
    
    // This contains the latest local updateTopic if the node is a SUBSCRIBER
    public Hashtable leafMetadata; // Contains mapping: Topic -> ESMContent
    public Hashtable intermediateMetadata ; // Contains mapping: Topic - > ESMTopicManager
    public Hashtable prevMetadata; // Contains mapping: Topic -> ESMContent




    public static class RankState {
        public double val;
        public NodeHandle child;
                                                                                
        public RankState(double _val, NodeHandle _child) {
            val = _val;
            child = _child;
        }
                                                                                
    }





    public class ESMTopicManager {
	public Topic topic;
	public String topicName;
	public Hashtable childData;
	// This esmContent will contain the aggregate values, its fields will be updated lazily only when propagation
	// is required. At that instant the rebuildESMContent() methos is innvoked

	// This just contains the aggregate value of the child metadata, note that it does not contain the local client if it is a subscriber, also this is updated explicitly using the rebuildESMContent() 
	public ESMContent esmContent;


	public ESMTopicManager(Topic topic, String topicName) {
	    this.topic = topic;
	    this.topicName = topicName;
	    childData = new Hashtable();
	    esmContent = new ESMContent(topicName, myLibraClient.cachedGNPCoord);
	}
	


	
	// builds the content as an aggregation
	public void rebuildESMContent() {
	    //System.out.println("rebuildESMContent() called");
	    // We will first remove stale child from here
	    Set keySet = childData.keySet();
	    Iterator it = keySet.iterator();
	    Vector toRemove = new Vector();
	    while(it.hasNext()) {
		NodeHandle child = (NodeHandle)it.next();
		if(!scribe.containsChild(topic,child)) {
		    toRemove.add(child);
		}
	    } 
	    for(int i=0; i<toRemove.size(); i++) {
		NodeHandle handle = (NodeHandle) toRemove.elementAt(i);
		childData.remove(handle);
	    }


	    // At this point the metadata contains all current children
	    ESMContent aggregateESMContent = null;
	    keySet = childData.keySet();
	    it = keySet.iterator();
	    while(it.hasNext()) {
		NodeHandle child = (NodeHandle)it.next();
		ESMContent esmContent = (ESMContent)childData.get(child);
		aggregateESMContent = aggregate(aggregateESMContent,esmContent);
	    }
	    if(aggregateESMContent == null) {
		esmContent = null;
	    } else {
		esmContent = new ESMContent(aggregateESMContent);
	    }

	}

	public void update(NodeHandle child, ESMContent content) {
	    childData.put(child, content);
	    
	}

	public ESMContent getESMContent(NodeHandle child) {
	    if(childData.containsKey(child)) {
		return ((ESMContent) childData.get(child));
	    } else {
		return null;
	    }
	}

	public void remove(NodeHandle child) {
	    childData.remove(child);
	}


    } 


    public ESMScribePolicy(Scribe scribe, MyLibraClient myLibraClient) {
	this.scribe = scribe;
	this.myLibraClient = myLibraClient;
	this.leafMetadata = new Hashtable();
	this.intermediateMetadata = new Hashtable();
	this.prevMetadata = new Hashtable();
	
    }


    // This is called by the queryGNP() routing in MyScribeClient whenever it sees a change in the GNP coordinates
    public void updateGNPCoord() {
	Set keySet = leafMetadata.keySet();
	Iterator it = keySet.iterator();
	while(it.hasNext()) {
	    Topic topic = (Topic)it.next();
	    ESMContent esmContent = (ESMContent)leafMetadata.get(topic);
	    esmContent.setGNPCoord(myLibraClient.cachedGNPCoord);
	} 
    }
    





    // This implements the aggregation function, the gnpCoord is set to the local GNP Coordinate
    public ESMContent aggregate(ESMContent content1, ESMContent content2) {
	ESMContent retContent = null;
	if((content1 == null) && (content2 == null)) {
	    retContent = null;
	} else if(content1 == null) {
	    retContent = new ESMContent(content2.topicName, true, null,-1,null,content2.paramsLoad, content2.paramsLoss, content2.time, 0,null,content2.descendants,myLibraClient.cachedGNPCoord);

	} else if(content2 == null) {
	    retContent = new ESMContent(content1.topicName, true, null,-1,null,content1.paramsLoad, content1.paramsLoss, content1.time, 0,null,content1.descendants,myLibraClient.cachedGNPCoord);
	    
	} else {
	    // Aggregate Function
	    if((!content1.topicName.equals(content2.topicName)) || (content1.descendants == 0) || (content2.descendants == 0) ) {
		 if(LibraTest.logLevel <= Logger.WARNING) myLibraClient.myPrint("ESM_AGGREGATE_ERROR: " + "C1.topicName= " + content1.topicName + " C2.topicName= " + content2.topicName + " C1.descendants= " + content1.descendants + " C2.descendants= " + content2.descendants, Logger.WARNING);
		     if(LibraTest.logLevel <= Logger.WARNING) myLibraClient.myPrint("ERROR: Attempt to aggregate ESMContent from different topics or the ESMContent has zero descendants", Logger.WARNING);
		     if(LibraTest.logLevel <= Logger.WARNING) myLibraClient.myPrint("ESMCONTENT1= " + content1, Logger.WARNING);
		     if(LibraTest.logLevel <= Logger.WARNING) myLibraClient.myPrint("ESMCONTENT2= " + content2, Logger.WARNING);
		    
		    System.exit(1);
	    } else {
		String myTopicName = content1.topicName;
		//if(content1.esmRunId != content2.esmRunId) {
		//if(LibraTest.logLevel <= Logger.WARNING) myLibraClient.myPrint("WARNING: ESM_AGGREGATE_ERROR:  " + "TopicName= " + myTopicName + " C1.esmContentRunId= " + content1.esmRunId + " C2.esmContentRunId= " + content2.esmRunId, Logger.WARNING);
		//}
		byte lowerESMRunId = 0;
		if(content1.esmRunId < content2.esmRunId) {
		    lowerESMRunId = content1.esmRunId;
		} else {
		    lowerESMRunId = content2.esmRunId;
		}
		byte esmRunId = lowerESMRunId;

		int mydescendants = content1.descendants + content2.descendants;
		int[] myparamsLoad = new int[2];
		myparamsLoad[0] = content1.paramsLoad[0] + content2.paramsLoad[0];
		myparamsLoad[1] = content1.paramsLoad[1] + content2.paramsLoad[1];
		int[] myparamsLoss = new int[1];
		myparamsLoss[0] = ((content1.descendants * content1.paramsLoss[0]) + (content2.descendants * content2.paramsLoss[0]))/ mydescendants;
		int mytime = ((content1.descendants * content1.time) + (content2.descendants * content2.time))/ mydescendants;
		retContent = new ESMContent(myTopicName, true,null,-1,null,myparamsLoad,myparamsLoss, mytime, 0,null,mydescendants, myLibraClient.cachedGNPCoord);
		retContent.setESMRunId(lowerESMRunId);
		}
	}
	return retContent;
    }
    
    
    public void intermediateNode(ScribeMessage message) {
	if((message instanceof AnycastMessage) && !(message instanceof SubscribeMessage)) {
	    // We track the path of the anycast requests in the overlay
	    AnycastMessage aMessage = (AnycastMessage) message;

	    if(aMessage.getContent() instanceof MyScribeContent) {
		// We will add the code of this node to track the path traversed
		((MyScribeContent)aMessage.getContent()).addToMsgPath(myLibraClient.endpoint.getLocalNodeHandle(), myLibraClient.bindIndex, myLibraClient.jvmIndex, myLibraClient.vIndex);
	    }
	}

    }
    
    
    /**
     * This method always return true;
     *
     * @param message The subscribe message in question
     * @param children The list of children who are currently subscribed
     * @param clients The list of clients are are currently subscribed
     * @return True.
     */
    public boolean allowSubscribe(SubscribeMessage message, ScribeClient[] clients, NodeHandle[] children) {

	if(!myLibraClient.topicsInitialized) {
	    return false;
	}
	NodeHandle newChild = (NodeHandle)message.getSubscriber();
	// In this method we implement the differernt Tree formation policies
	Topic topic = message.getTopic();
	 if(LibraTest.logLevel <= 850) myLibraClient.myPrint("allowSubscribe(" + topic + ", " + newChild + ")", 850);
	String myTopicName = myLibraClient.topic2TopicName(topic);
	int tNumber = myLibraClient.topicName2Number(myTopicName);

	if(myLibraClient.printEnable(tNumber)) {
	     if(LibraTest.logLevel <= 850) myLibraClient.myPrint("SysTime: " + System.currentTimeMillis() + " allowSubscribe(" + message + ", children= [", 850);
	    for(int i=0; i< children.length; i++) {
		myLibraClient.myPrint(children[i] + ",", 850);
	    }
	    myLibraClient.myPrint("]", 850);
	}


	int policy = getPolicy(tNumber);
	if((policy == ESMScribePolicy.CENTRALRANDOM) || (policy == ESMScribePolicy.CENTRALOPTIMAL) || (policy == ESMScribePolicy.ESMCENTRALRANDOM) || (policy == ESMScribePolicy.ESMCENTRALOPTIMAL)) {
	    // The tree hangs of from the root only
	    if(myLibraClient.isRoot(topic)) {
		return true;
	    } else {
		return false;
	    }

	} else {
	    // Normal tree formation
	    return true;
	}
    }

    /**
     * Simply adds the parent and children in order, which implements a
     * depth-first-search.
     *
     * @param message The anycast message in question
     * @param parent Our current parent for this message's topic
     * @param children Our current children for this message's topic
     */
    public void directAnycast(AnycastMessage message, NodeHandle parent, NodeHandle[] children) {

	if(message instanceof SubscribeMessage) {
	    // We have thew option of specifying the next hop for the Subscribe message

	} else {
	  
	    // We will cause a failure if the total number of traversals (visited.size + toVisit.size) of the anycast message has already reached a threshold
	    if(message.getVisitedSize() >= LibraTest.MAXANYCASTWILLTRAVERSE) {
		
		String contentType = "";
		if(message.getContent() instanceof GrpMetadataRequestContent) {
		    contentType = "GrpMetadataRequestContent";
		} else if(message.getContent() instanceof MyScribeContent) {
		    contentType = "MyScribeContent";
		}

		if(LibraTest.logLevel <= 850) myLibraClient.myPrint("SysTime: " + System.currentTimeMillis() + " MAXANYCASTWILLTRAVERSE threshold reached with CONTENTTYPE: " + contentType + " in directAnycast(" + message + ")", 850);
		return;

	    }


	    if(message.getContent() instanceof GrpMetadataRequestContent) {
		// If we are the root we handle the message and respond back else we propagate the request towards the root
		Topic myTopic = message.getTopic();
		GrpMetadataRequestContent myContent = (GrpMetadataRequestContent)message.getContent();
		String myTopicName = myContent.topicName;
		int topicNumber = myLibraClient.topicName2Number(myTopicName);
		NodeHandle anycastRequestor = myContent.from;
		int seq = myContent.seq;


		if(myLibraClient.isRoot(myTopic)) {
		    //if(myLibraClient.getTreeStatus(topicNumber) != myLibraClient.NONTREEMEMBER) {
		    ESMContent esmContentRoot = null;
		    ESMContent esmContentLeaf = null;
		    ESMContent esmContentIntermediate = null;
		    if(leafMetadata.containsKey(myTopic)) {
			esmContentLeaf= (ESMContent) leafMetadata.get(myTopic);		
		    }
		    ESMScribePolicy.ESMTopicManager manager = null;
		    if(intermediateMetadata.containsKey(myTopic)) {
			manager = (ESMScribePolicy.ESMTopicManager)intermediateMetadata.get(myTopic);
			manager.rebuildESMContent();
			esmContentIntermediate = manager.esmContent;
		    }
		    esmContentRoot = aggregate(esmContentLeaf,esmContentIntermediate);
		    if(esmContentRoot != null) {
			if(myLibraClient.printEnable(topicNumber)) {
			    if(LibraTest.logLevel <= 850) myLibraClient.myPrint("SysTime: " + System.currentTimeMillis() + " Node "+ myLibraClient.endpoint.getLocalNodeHandle()+" PROVIDINGGRPMETADATA to " + anycastRequestor + "for Topic[ "+ myTopicName + " ] " + myTopic + " content: " + myContent , 850);
			    
			    
			}
			if (anycastRequestor != null) {
			    myLibraClient.endpoint.route(null, new MyGrpMetadataAckMsg(esmContentRoot, myLibraClient.endpoint.getLocalNodeHandle(), myTopic, seq), anycastRequestor);
			}
		    } else {
			if(LibraTest.logLevel <= 875) myLibraClient.myPrint("WARNING: GrpMetadataRequestContent for Topic[ "+ myTopicName + " has null metadata", 875);
			// We return a failure here
			// We will send an anycast failure message
			AnycastFailureMessage aFailMsg = new AnycastFailureMessage(myLibraClient.endpoint.getLocalNodeHandle(), message.getTopic(), message.getContent());
			myLibraClient.endpoint.route(null, aFailMsg, anycastRequestor);

		    }
		    
		} else {
		    if (parent != null) {
			message.addFirst(parent);
		    }
		}

		/*  We assume that GrpResource request are handle only by the root for now to get rid of the downward propagation 
		// implements the same functionality as DefaultScribe policy
		 if (parent != null) {
		     message.addLast(parent);
		 }
		 


		 
		 // now randomize the children list
		 for (int i=0; i<children.length; i++) {
		     int j = rng.nextInt(children.length);
		     int k = rng.nextInt(children.length);
		     NodeHandle tmp = children[j];
		     children[j] = children[k];
		     children[k] = tmp;
		 }
		 
		 Vector sortedSatisfied = new Vector();
		 for (int l = 0; l < children.length; l++) {
		     NodeHandle handle = children[l];
		     sortedSatisfied.add(handle);
		 }
		 
		 while(sortedSatisfied.size() > LibraTest.MAXSATISFIEDCHILDREN) {
		     sortedSatisfied.remove(sortedSatisfied.size() -1);
		 }
		 
		 for(int i=0; i < sortedSatisfied.size(); i++) {
		     NodeHandle handle = (NodeHandle) sortedSatisfied.elementAt(sortedSatisfied.size() - i -1);
		     message.addFirst(handle);
		 }
		 */
		 




	    } else if(message.getContent() instanceof MyScribeContent) {
		MyScribeContent myContent = (MyScribeContent)message.getContent();
		if(myContent == null) {
		     if(LibraTest.logLevel <= Logger.WARNING) myLibraClient.myPrint("ERROR: AnycastMsg= " + message, Logger.WARNING);
		    System.exit(1);
		}
		
		String myTopicName = myContent.topicName;
		int topicNumber = myLibraClient.topicName2Number(myTopicName);
		NodeHandle anycastRequestor = myContent.from;
		byte[] requestorIdArray = myContent.getESMIdArray();
		GNPCoordinate requestorGNPCoord = myContent.getGNPCoord();
		
		
		if(myLibraClient.printEnable(topicNumber)) {
		    if(LibraTest.logLevel <= 875) myLibraClient.myPrint("SysTime: " + System.currentTimeMillis() + " directAnycast(" + myContent.globalId + ", parent= " + parent +  ")", 875);
		    if(LibraTest.logLevel <= 850) myLibraClient.myPrint("SysTime: " + System.currentTimeMillis() + " directAnycast(" + message + " parent= " + parent + " children= [", 850);
		    for(int i=0; i< children.length; i++) {
			myLibraClient.myPrint(children[i] + ",", 850);
		    }
		    myLibraClient.myPrint("]", 850);
		}
		
		// This will contain the list of nodes that satisfied the basic predicates
		Vector satisfied = new Vector();
		
		Topic topic = message.getTopic();
		if (parent != null) {
		    message.addLast(parent);
		}
		
		
		// now randomize the children list
		for (int i = 0; i < children.length; i++) {
		    int j = rng.nextInt(children.length);
		    int k = rng.nextInt(children.length);
		    NodeHandle tmp = children[j];
		    children[j] = children[k];
		    children[k] = tmp;
		}
		ESMTopicManager esmManager = (ESMTopicManager) intermediateMetadata.get(topic);
		for (int l = 0; l < children.length; l++) {
		    boolean toAdd = false;
		    NodeHandle handle = children[l];
		    if(esmManager == null) {
			// We will add the child
			toAdd = true;
		    } else {
			ESMContent esmContent = esmManager.getESMContent(handle);
			if(predicateVerified(anycastRequestor, myTopicName, handle, topic, esmContent, requestorIdArray, requestorGNPCoord, myContent.pathLength, myContent.paramsPath)) {
			    toAdd = true;
			} else {
			    toAdd = false;
			}
		    }
		    if(toAdd) {
			// For now we randomly allow all children who satisfy predicates
			// TODO - We should also order the children based on (location,loadMetricValues,lossRate,descendents)
			satisfied.add(handle);
		    }
		}
		
		
		Vector sortedSatisfied = new Vector();
		// This vector will contain the satisfied nodehandles in a sorted order with the high priority ones at top of vector
		
		int policy = getPolicy(topicNumber);
		if((policy == CENTRALRANDOM) || (policy == ESMCENTRALRANDOM)) {
		    // Also check to see if the local node is subscribed
		    
		    // We had already randomized the children
		    for(int i=0; i< satisfied.size(); i++) {
			NodeHandle handle = (NodeHandle) satisfied.elementAt(i);
			sortedSatisfied.add(handle);
		    }
		} else if((policy == NAIVESCRIBE) || (policy == ESMNAIVESCRIBE)) {
		    // We had already randomized the children
		    for(int i=0; i< satisfied.size(); i++) {
			NodeHandle handle = (NodeHandle) satisfied.elementAt(i);
			sortedSatisfied.add(handle);
		    }
		} else if((policy == CENTRALOPTIMAL) || (policy == ESMCENTRALOPTIMAL)) {
		    // Check for local node and bias with locality only
		    
		    Vector sorted = sortWRTLocality(topic,satisfied, requestorGNPCoord);
		    for(int i=0; i< sorted.size(); i++) {
			// NOTE : The way the addFirst() works, causes us to call addFirst in reverse order
			NodeHandle handle = (NodeHandle) sorted.elementAt(i);
			sortedSatisfied.add(handle);
		    }
		    
		} else if((policy == LIBRA) || (policy == ESMLIBRA)) {
		    
		    if(LibraTest.ESMLIBRA_POLICY == ESMLIBRA_RANDOM) {
			// We had already randomized, we insert in random order
			for(int i=0; i< satisfied.size(); i++) {
			    NodeHandle handle = (NodeHandle) satisfied.elementAt(i);
			    sortedSatisfied.add(handle);
			}
			
		    } else if(LibraTest.ESMLIBRA_POLICY == ESMLIBRA_BANDWIDTH) {
			// Bias with bandwidth only
			Vector sorted = sortWRTBandwidth(topic,satisfied);
			for(int i=0; i< sorted.size(); i++) {
			    NodeHandle handle = (NodeHandle) sorted.elementAt(i);
			    sortedSatisfied.add(handle);
			}
		    } else if(LibraTest.ESMLIBRA_POLICY == ESMLIBRA_LOCALITY) {
			// Bias with locality only
			Vector sorted = sortWRTLocality(topic,satisfied,requestorGNPCoord);
			for(int i=0; i< sorted.size(); i++) {
			    NodeHandle handle = (NodeHandle) sorted.elementAt(i);
			    sortedSatisfied.add(handle);
			}
		    } else if(LibraTest.ESMLIBRA_POLICY == ESMLIBRA_DEPTH) {
		    // Bias with depth only
			Vector sorted = sortWRTDepth(topic,satisfied);
			for(int i=0; i< sorted.size(); i++) {
			    NodeHandle handle = (NodeHandle) sorted.elementAt(i);
			    sortedSatisfied.add(handle);
			}
		    } else if(LibraTest.ESMLIBRA_POLICY == ESMLIBRA_TIME) {
			// Bias with depth only
			Vector sorted = sortWRTTime(topic,satisfied);
			for(int i=0; i< sorted.size(); i++) {
			    NodeHandle handle = (NodeHandle) sorted.elementAt(i);
			    sortedSatisfied.add(handle);
			}   
		    } else if(LibraTest.ESMLIBRA_POLICY == ESMLIBRA_COMBINED) {
		    // This does a weighted metric of the positions in vectors: Locality/Bandwidth/Depth
			Vector infoList = new Vector(); // This contains the unsorted vector of the (handle, metric values)
			Vector sortedInfoList = new Vector(); // This contains the sorted vector of the (handle, metric values)
			
			Vector sorted_bandwidth = sortWRTBandwidth(topic,satisfied);
			Vector sorted_locality = sortWRTLocality(topic,satisfied,requestorGNPCoord);
			Vector sorted_depth = sortWRTDepth(topic,satisfied);
			Vector sorted_time = sortWRTTime(topic,satisfied);
			for(int i=0; i< satisfied.size(); i++) {
			    NodeHandle handle = (NodeHandle) satisfied.elementAt(i);
			    double metric = 0.0 * sorted_bandwidth.indexOf(handle) + 1.0 * sorted_locality.indexOf(handle) + 0.0 * sorted_depth.indexOf(handle) + 1.0 * sorted_time.indexOf(handle);
			    infoList.add(new RankState(metric,handle));
			}
			// We will now do a removal MINIMUM sort on this
			while(!infoList.isEmpty()) {
			    double val;
			    double minVal;
			    RankState state;
			    RankState chosenState;
			    state = (RankState)infoList.elementAt(0);
			    minVal = state.val;
			    chosenState = state;
			    for(int index = 1; index < infoList.size(); index++) {
				state = (RankState)infoList.elementAt(index);
				val = state.val;
				if(val < minVal) {
				    minVal = val;
				    chosenState = state;
				}
			    }
			    sortedInfoList.add(chosenState);
			    infoList.remove(chosenState);
			    
		    }
			
			// Here we just copy the sorted elements to the desired vector
			for(int i=0; i< sortedInfoList.size(); i++) {
			    RankState state = (RankState) sortedInfoList.elementAt(i);
			    sortedSatisfied.add(state.child);
			}
			
		    } else {
			// Default is RANDOM
			// We had already randomized, we insert in random order
			for(int i=0; i< satisfied.size(); i++) {
			    NodeHandle handle = (NodeHandle) satisfied.elementAt(i);
			    sortedSatisfied.add(handle);
			}
			
			
		    }
		    

		
		
		} else {
		    // This is the default strategy
		    // We had already randomized the children
		    for(int i=0; i< satisfied.size(); i++) {
			NodeHandle handle = (NodeHandle) satisfied.elementAt(i);
			sortedSatisfied.add(handle);
		    }
		    
		}


		// At this point we bias towards the leafs to reduce the number of anycast hops
		// We iterate down the list, finding the ones that are agg/leafs and add them in their respective orders to a new Vector
		Vector sortedSatisfiedLeafbiased = sortWRTLeafs(topic,sortedSatisfied);
		
 
		
		// At this point we might wanna reduce the length of the potential children to keep the anycast 
		// traversal time bounded as well as the anycast messages to smaller sizes. Note that this is OK
		// since these group members have a high chance of accepting the anycast since we have already verified
		// their metadata. In the basis Scribe framework where no metadata was propagated this would not work
		while(sortedSatisfiedLeafbiased.size() > LibraTest.MAXSATISFIEDCHILDREN) {
		    sortedSatisfiedLeafbiased.remove(sortedSatisfiedLeafbiased.size() -1);
		}


	      
		// Note: The way message.addFirst() works , causes to call this method in the reverse order on the list
		for(int i=0; i < sortedSatisfiedLeafbiased.size(); i++) {
		    NodeHandle handle = (NodeHandle) sortedSatisfiedLeafbiased.elementAt(sortedSatisfiedLeafbiased.size() - i -1);


		    // At this point we ensure that we do not make the anycast message traversal longer than MAXANYCASTWILLTRAVERSE, if this turns out the case we remove nodes from the end of the toVisit() since the nodes that are the lowest priority nodes in the anycast traversal
		    if((message.getVisitedSize() + message.getToVisitSize()) >= LibraTest.MAXANYCASTWILLTRAVERSE) {
			message.removeLastFromToVisit();
		    }		    
		    message.addFirst(handle);
		    // We also log the information of whom we chose and why
		    if(esmManager != null) {
			String choice = myContent.globalId + " child: " + handle;
			ESMContent esmContent = esmManager.getESMContent(handle);
			if(esmContent !=null) {
			    choice = choice + " Agg: " + esmContent.hasAggregateFlagSet() + ", Dist: " + esmContent.getDistance(requestorGNPCoord) + ", Bandwidth: " + esmContent.getSpareBandwidth() + ", Time: " + esmContent.getRemainingTime() + ", Depth: " + esmContent.getDepth() + ", Loss: " + esmContent.getLoss();
			} else {
			    choice = choice + " NULL";
			}
			//if(LibraTest.logLevel <= 875) myLibraClient.myPrint(choice, 875);	
			if(LibraTest.logLevel <= 850) myLibraClient.myPrint(choice, 850);	
		    }
		}
		
		
		
	    
	    }
	    
	}
    }
    
    

    
    public Vector sortWRTLeafs(Topic topic, Vector unsorted) {
	Vector sorted = new Vector();
	ESMTopicManager esmManager = (ESMTopicManager) intermediateMetadata.get(topic);
	if(esmManager == null) {
	    // We will add all the children as is
	    for(int i=0; i< unsorted.size(); i++) {
		NodeHandle child = (NodeHandle) unsorted.elementAt(i);
		sorted.add(child);
	    }
	    
	} else {
	    Vector infoList = new Vector();
	    Vector sortedInfoList = new Vector();
	    for(int i=0; i< unsorted.size(); i++) {
		NodeHandle child = (NodeHandle) unsorted.elementAt(i);
		ESMContent esmContent = esmManager.getESMContent(child);
		RankState state;		
		// We rank the Leafs > Aggreg > UNDEF
		if(esmContent == null) {
		    state = new RankState(Double.MIN_VALUE, child);
		} else if(!esmContent.hasAggregateFlagSet()){
		    state = new RankState(2, child);
		} else {
		    state = new RankState(1, child);
		}
		infoList.add(state);
	    }

	    // We will now do a removal MAXIMUM sort on this
	    while(!infoList.isEmpty()) {
	    	double val;
		double maxVal;
		RankState state;
		RankState chosenState;
		state = (RankState)infoList.elementAt(0);
		maxVal = state.val;
		chosenState = state;
		for(int index = 1; index < infoList.size(); index++) {
		    state = (RankState)infoList.elementAt(index);
		    val = state.val;
		    if(val > maxVal) { // It is important to retain non-shuffling during the equal operation to ensure it is order preserving when we have equal values
			maxVal = val;
			chosenState = state;
		    }
		}
		sortedInfoList.add(chosenState);
		infoList.remove(chosenState);
		
	    }
	    for(int i=0; i< sortedInfoList.size(); i++) {
		RankState state = (RankState) sortedInfoList.elementAt(i);
		sorted.add(state.child);
	    }
	    
	}

	return sorted;
	
    }












    // Returns a sorted list based on locality, the topic helps to choose amongst the children in the 
    // intermediate metadata for this topic.
    // First element - closest, last element - farthest
    public Vector sortWRTLocality(Topic topic, Vector unsorted, GNPCoordinate requestorCoord) {
	Vector sorted = new Vector();
	ESMTopicManager esmManager = (ESMTopicManager) intermediateMetadata.get(topic);
	if((esmManager == null) || (requestorCoord == null)) {
	    // We will add all the children as is
	    for(int i=0; i< unsorted.size(); i++) {
		NodeHandle child = (NodeHandle) unsorted.elementAt(i);
		sorted.add(child);
	    }
	    
	} else {
	    Vector infoList = new Vector();
	    Vector sortedInfoList = new Vector();
	    for(int i=0; i< unsorted.size(); i++) {
		NodeHandle child = (NodeHandle) unsorted.elementAt(i);
		ESMContent esmContent = esmManager.getESMContent(child);
		RankState state;		
		if((esmContent == null) || (esmContent.getGNPCoord() == null)) {
		    state = new RankState(Double.MAX_VALUE, child);
		} else {
		    GNPCoordinate childCoord = esmContent.getGNPCoord();
		    state = new RankState(requestorCoord.distance(childCoord), child);
		}
		infoList.add(state);
	    }

	    // We will now do a removal MINIMUM sort on this
	    while(!infoList.isEmpty()) {
	    	double val;
		double minVal;
		RankState state;
		RankState chosenState;
		state = (RankState)infoList.elementAt(0);
		minVal = state.val;
		chosenState = state;
		for(int index = 1; index < infoList.size(); index++) {
		    state = (RankState)infoList.elementAt(index);
		    val = state.val;
		    if(val < minVal) {
			minVal = val;
			chosenState = state;
		    }
		}
		sortedInfoList.add(chosenState);
		infoList.remove(chosenState);
		
	    }
	    for(int i=0; i< sortedInfoList.size(); i++) {
		RankState state = (RankState) sortedInfoList.elementAt(i);
		sorted.add(state.child);
	    }
	    
	}
	
	return sorted;
	
    }
    


    // Returns a sorted list based on sparebandwidth, the topic helps to choose amongst the children in the 
    // intermediate metadata for this topic
    // First element - maximum bandwidth , last element - minimum bandwidth
    public Vector sortWRTBandwidth(Topic topic, Vector unsorted) {
	Vector sorted = new Vector();
	ESMTopicManager esmManager = (ESMTopicManager) intermediateMetadata.get(topic);
	if(esmManager == null) {
	    // We will add all the children as is
	    for(int i=0; i< unsorted.size(); i++) {
		NodeHandle child = (NodeHandle) unsorted.elementAt(i);
		sorted.add(child);
	    }
	    
	} else {
	    Vector infoList = new Vector();
	    Vector sortedInfoList = new Vector();
	    for(int i=0; i< unsorted.size(); i++) {
		NodeHandle child = (NodeHandle) unsorted.elementAt(i);
		ESMContent esmContent = esmManager.getESMContent(child);
		RankState state;		
		if(esmContent == null) {
		    state = new RankState(Double.MIN_VALUE, child);
		} else {
		    int spareSlots = esmContent.getSpareBandwidth();
		    state = new RankState((double)spareSlots, child);
		}
		infoList.add(state);
	    }

	    // We will now do a removal MAXIMUM sort on this
	    while(!infoList.isEmpty()) {
	    	double val;
		double maxVal;
		RankState state;
		RankState chosenState;
		state = (RankState)infoList.elementAt(0);
		maxVal = state.val;
		chosenState = state;
		for(int index = 1; index < infoList.size(); index++) {
		    state = (RankState)infoList.elementAt(index);
		    val = state.val;
		    if(val > maxVal) {
			maxVal = val;
			chosenState = state;
		    }
		}
		sortedInfoList.add(chosenState);
		infoList.remove(chosenState);
		
	    }
	    for(int i=0; i< sortedInfoList.size(); i++) {
		RankState state = (RankState) sortedInfoList.elementAt(i);
		sorted.add(state.child);
	    }
	    
	}

	return sorted;
	
    }



  // Returns a sorted list based on sparebandwidth, the topic helps to choose amongst the children in the 
    // intermediate metadata for this topic
    // First element - maximum bandwidth , last element - minimum bandwidth
    public Vector sortWRTTime(Topic topic, Vector unsorted) {
	Vector sorted = new Vector();
	ESMTopicManager esmManager = (ESMTopicManager) intermediateMetadata.get(topic);
	if(esmManager == null) {
	    // We will add all the children as is
	    for(int i=0; i< unsorted.size(); i++) {
		NodeHandle child = (NodeHandle) unsorted.elementAt(i);
		sorted.add(child);
	    }
	    
	} else {
	    Vector infoList = new Vector();
	    Vector sortedInfoList = new Vector();
	    for(int i=0; i< unsorted.size(); i++) {
		NodeHandle child = (NodeHandle) unsorted.elementAt(i);
		ESMContent esmContent = esmManager.getESMContent(child);
		RankState state;		
		if(esmContent == null) {
		    state = new RankState(Double.MIN_VALUE, child);
		} else {
		    int remainingTime = esmContent.getRemainingTime();
		    state = new RankState((double)remainingTime, child);
		}
		infoList.add(state);
	    }

	    // We will now do a removal MAXIMUM sort on this
	    while(!infoList.isEmpty()) {
	    	double val;
		double maxVal;
		RankState state;
		RankState chosenState;
		state = (RankState)infoList.elementAt(0);
		maxVal = state.val;
		chosenState = state;
		for(int index = 1; index < infoList.size(); index++) {
		    state = (RankState)infoList.elementAt(index);
		    val = state.val;
		    if(val > maxVal) {
			maxVal = val;
			chosenState = state;
		    }
		}
		sortedInfoList.add(chosenState);
		infoList.remove(chosenState);
		
	    }
	    for(int i=0; i< sortedInfoList.size(); i++) {
		RankState state = (RankState) sortedInfoList.elementAt(i);
		sorted.add(state.child);
	    }
	    
	}

	return sorted;
	
    }



    // Returns a sorted list based on depth (pathlength field in esmcontent), the topic helps to choose amongst the children in the 
    // intermediate metadata for this topic.
    // First element - minimum depth, last element - greatest depth 
    public Vector sortWRTDepth(Topic topic, Vector unsorted) {
	Vector sorted = new Vector();
	ESMTopicManager esmManager = (ESMTopicManager) intermediateMetadata.get(topic);
	if(esmManager == null) {
	    // We will add all the children as is
	    for(int i=0; i< unsorted.size(); i++) {
		NodeHandle child = (NodeHandle) unsorted.elementAt(i);
		sorted.add(child);
	    }
	    
	} else {
	    Vector infoList = new Vector();
	    Vector sortedInfoList = new Vector();
	    for(int i=0; i< unsorted.size(); i++) {
		NodeHandle child = (NodeHandle) unsorted.elementAt(i);
		ESMContent esmContent = esmManager.getESMContent(child);
		RankState state;		
		if((esmContent == null) || esmContent.hasAggregateFlagSet() || (esmContent.getDepth() < 0)) {
		    state = new RankState(Double.MAX_VALUE, child);
		} else {
		    int depth = esmContent.getDepth();
		    state = new RankState((double)depth, child);
		}
		infoList.add(state);
	    }

	    // We will now do a removal MINIMUM sort on this
	    while(!infoList.isEmpty()) {
	    	double val;
		double minVal;
		RankState state;
		RankState chosenState;
		state = (RankState)infoList.elementAt(0);
		minVal = state.val;
		chosenState = state;
		for(int index = 1; index < infoList.size(); index++) {
		    state = (RankState)infoList.elementAt(index);
		    val = state.val;
		    if(val < minVal) {
			minVal = val;
			chosenState = state;
		    }
		}
		sortedInfoList.add(chosenState);
		infoList.remove(chosenState);
		
	    }
	    for(int i=0; i< sortedInfoList.size(); i++) {
		RankState state = (RankState) sortedInfoList.elementAt(i);
		sorted.add(state.child);
	    }
	    
	}
	
	return sorted;
	
    }





    // Verifies that the handle satisfies the predicate 
    //   a) does not form ESM tree loops
    //   b) is not overloaded
    //   c) has good performance
    public boolean predicateVerified(NodeHandle anycastRequestor, String topicName, NodeHandle handle, Topic topic, ESMContent esmContent, byte[] requestorIdArray, GNPCoordinate requestorGNPCoord, int pathLengthRequestor, byte[] paramsPathRequestor) {
	int topicNumber = myLibraClient.topicName2Number(topicName);
	boolean printEnable = myLibraClient.printEnable(topicNumber);
	if(esmContent == null) {
	    if(printEnable) {
		 if(LibraTest.logLevel <= 850) myLibraClient.myPrint("pVerify(" + handle + ", " + "esmContent=null" + ", " + "ret=false", 850);
	    }
	    return false;
	}

	// If the child is the requestor who is a leaf then there is no point sending him the request. Warning: However, if it has aggregate flag set, then it should preferable not have come up to this parent in the first place. That is if an anycast request is originated from an intermediate node in the Scribe tree we should handle it by first calling directAnycast() on the local node 
	if(!esmContent.hasAggregateFlagSet() && anycastRequestor.equals(handle)) {
	    if(printEnable) {
		 if(LibraTest.logLevel <= 850) myLibraClient.myPrint("pVerify(" + handle + ", " + "esmContent=Leaf_sameAsRequestor" + ", " + "ret=false", 850);
	    }
	    return false;
	}


	// We check here if the handle satisfies the predicate
	if(esmContent.hasAggregateFlagSet()) {
	    // Note : Implement the logic for choosing subtrees
	    // For now we do not do fancy aggregation of metrics
	    if(!esmContent.hasSpareBandwidth()) {
		if(printEnable) {
		     if(LibraTest.logLevel <= 850) myLibraClient.myPrint("ESMContentRunId= " + esmContent.esmRunId + " pVerify(" + handle + ", " + "esmContent=Aggreg_noBandwidth" + ", " + "ret=false" + " esmContent= " + esmContent, 850);
		}
		return false;

		// The performance check is being removed to enable fast reconvergence
		//} else if(!esmContent.hasGoodPerformance()) {
		//if(printEnable) {
		//  myLibraClient.myPrint("ESMContentRunId= " + esmContent.esmRunId + " pVerify(" + handle + ", " + "esmContent=Aggreg_highLoss" + ", " + "ret=false" + " esmContent= " + esmContent, 850);
		//}
		//return false;
	    } else {
		if(printEnable) {
		     if(LibraTest.logLevel <= 850) myLibraClient.myPrint("ESMContentRunId= " + esmContent.esmRunId + " pVerify(" + handle + ", " + "esmContent=Aggreg_SpareB" + ", " + "ret=true " + " esmContent= " + esmContent, 850);
		}
		return true;
	    }
	} else {
	    // Leaf
	    boolean toReturn = true;
	    // We will check all conditions and make it false if any condition is violated
	    
	    if(!esmContent.hasSpareBandwidth()) {
		if(printEnable) {
		     if(LibraTest.logLevel <= 850) myLibraClient.myPrint("ESMContentRunId= " + esmContent.esmRunId + " pVerify(" + handle + ", " + "esmContent=Leaf_noBandwidth" + ", " + "ret=false" + " esmContent= " + esmContent, 850);
		}
		toReturn = false;
	    } 

	    if(!esmContent.hasGoodPerformance()) {
		if(!LibraTest.ENABLEFASTCONVERGENCE) {
		    if(printEnable) {
			 if(LibraTest.logLevel <= 850) myLibraClient.myPrint("ESMContentRunId= " + esmContent.esmRunId + " pVerify(" + handle + ", " + "esmContent=Leaf_highLoss_FastConvergenceFlagDisabled" + ", " + "ret=false" + " esmContent= " + esmContent, 850);
		    }
		    toReturn = false;
		} else {
		    if(!esmContent.allowFastConvergence(pathLengthRequestor, paramsPathRequestor, requestorIdArray)) {
			if(printEnable) {
			     if(LibraTest.logLevel <= 850) myLibraClient.myPrint("ESMContentRunId= " + esmContent.esmRunId + " pVerify(" +  handle + ", " + "esmContent=Leaf_highLoss_FastConvergenceFlagEnabled_IMPROPERORDERING" + ", " + "ret=false" + " esmContent= " + esmContent, 850);
			}
			toReturn = false;			    
		    }
		}
	    }  

	    if(!esmContent.hasNoLoops(requestorIdArray)) {
		if(printEnable) {
		     if(LibraTest.logLevel <= 850) myLibraClient.myPrint("ESMContentRunId= " + esmContent.esmRunId + " pVerify(" + handle + ", " + "esmContent=Leaf_hasLoops" + ", " + "ret=false" + " esmContent= " + esmContent, 850);
		}
		toReturn = false;
	    } 

	    if(toReturn) {
		if(printEnable) {
		    double gnpDist;
		    if(requestorGNPCoord == null) {
			gnpDist = Double.MAX_VALUE;
		    } else {
			gnpDist = requestorGNPCoord.distance(esmContent.getGNPCoord());
		    }
		     if(LibraTest.logLevel <= 850) myLibraClient.myPrint("ESMContentRunId= " + esmContent.esmRunId + " pVerify(" + handle + ", " + "esmContent=Leaf_SpareB_LowLossORFastConvergenceProperOrdering_NoLoops" + ", " + "ret=true" + " Depth= " + esmContent.getDepth() + " GNPDist= " + gnpDist +  " SpareB= " + esmContent.getSpareBandwidth() + " Time= " + esmContent.time, 850);
		}
		return true;
	    } else {
		return false;
	    }
	    
	    
	}
	
    }
    
    /**
     * Informs this policy that a child was added to a topic - the topic is free
     * to ignore this upcall if it doesn't care.
     *
     * @param topic The topic to unsubscribe from
     * @param child The child that was added
     */
    public void childAdded(Topic topic, NodeHandle child) {
    }

    /**
     * Informs this policy that a child was removed from a topic - the topic is
     * free to ignore this upcall if it doesn't care.
     *
     * @param topic The topic to unsubscribe from
     * @param child The child that was removed
     */
    public void childRemoved(Topic topic, NodeHandle child) {
	String topicName = myLibraClient.topic2TopicName(topic);
	int tNumber = myLibraClient.topicName2Number(topicName);
	// We will remove the metadata for the child in this topic
	if(intermediateMetadata.containsKey(topic)) {
	    ESMTopicManager esmManager = (ESMTopicManager) intermediateMetadata.get(topic);
	    esmManager.remove(child);
	    if(myLibraClient.printEnable(tNumber)) {
		 if(LibraTest.logLevel <= 850) myLibraClient.myPrint("SysTime: " + System.currentTimeMillis() + " ESMContentRunId= " + " childRemoved( " + topic + ", " + child + ")", 850);
	    }
	    if(LibraTest.IMMEDIATEUPDATEPROP) {
		// WARNING : The line below needs to be enabled to allow propagation of the esmcontent at the intermediate node on  a trigger basis, otherwise it will be propagated only using the periodic load propagation thread
		myLibraClient.sendRefreshUpdateForTopic(tNumber, true);
	    }
		
	}
    }

    // Assumption : At the Scribe layer this upcall is made only for a valid child in a valid topic
    public void updateChild(Topic topic, NodeHandle child, ScribeContent content) {
	if(content instanceof ESMContent) {
	    ESMContent mycontent = (ESMContent)content;
	    String topicName = mycontent.topicName;
	    int tNumber = myLibraClient.topicName2Number(topicName);
	    if(!intermediateMetadata.containsKey(topic)) {
		intermediateMetadata.put(topic, new ESMTopicManager(topic, topicName));
	    }
	    ESMTopicManager manager = (ESMTopicManager)intermediateMetadata.get(topic);
	    if(!manager.topicName.equals(topicName)) {
		if(LibraTest.logLevel <= Logger.WARNING) myLibraClient.myPrint("ERROR: The topicNames of the different ESMContent in the sme ESMTopicManager do not match in updateChild(" + topic + "," + child + "," + mycontent, Logger.WARNING);
		if(LibraTest.logLevel <= Logger.WARNING) myLibraClient.myPrint("ERROR: topicNameRecv: " + topicName + " manager.topicName: " + manager.topicName + " mappedTopicName: " + myLibraClient.topic2TopicName(topic), Logger.WARNING);
		System.exit(1);
	    }
	    manager.update(child,mycontent);
	    if(myLibraClient.printEnable(tNumber)) {
		if(LibraTest.logLevel <= 850) myLibraClient.myPrint("updateChild( " + topic + ", " + child + ", " + mycontent + ")", 850);
	    }
	    

	    if(LibraTest.IMMEDIATEUPDATEPROP) {
		// WARNING : The line below needs to be enabled to allow propagation of the esmcontent at the intermediate node on  a trigger basis, otherwise it will be propagated only using the periodic load propagation thread
		myLibraClient.sendRefreshUpdateForTopic(tNumber, true);
	    }
	    
	    if(LibraTest.ENABLEUPDATEACK) {
		// We will also send an Ack back
		ESMAckContent esmAckContent = new ESMAckContent(mycontent.topicName);
		scribe.sendUpdateAck(topic,child,esmAckContent);
	    }
	} else if(content instanceof AggregateESMContent) {
	    // This type of content is used to implement the aggregation of updatesy
	    AggregateESMContent mycontent = (AggregateESMContent)content;
	    if(LibraTest.logLevel <= 850) myLibraClient.myPrint("updateChild( " + mycontent + " )", 850);

	    for(int i=0; i< mycontent.getNumUpdates(); i++) {
		AggregateESMContent.UpdatePair pair = (AggregateESMContent.UpdatePair) mycontent.allTopics.elementAt(i);
		ESMContent aggregContent = pair.content;
		Topic aggregTopic = pair.topic;
		String aggregTopicName = aggregContent.topicName;
		int aggregTNumber = myLibraClient.topicName2Number(aggregTopicName);
		if(!intermediateMetadata.containsKey(aggregTopic)) {
		    intermediateMetadata.put(aggregTopic, new ESMTopicManager(aggregTopic, aggregTopicName));
		}
		ESMTopicManager manager = (ESMTopicManager)intermediateMetadata.get(aggregTopic);
		if(!manager.topicName.equals(aggregTopicName)) {
		    if(LibraTest.logLevel <= Logger.WARNING) myLibraClient.myPrint("ERROR: The topicNames of the different ESMContent in the sme ESMTopicManager do not match in updateChild(AggregESMContent)", Logger.WARNING);
		    if(LibraTest.logLevel <= Logger.WARNING) myLibraClient.myPrint("ERROR: topicNameRecv: " + aggregTopicName + " manager.topicName: " + manager.topicName + " mappedTopicName: " + myLibraClient.topic2TopicName(aggregTopic), Logger.WARNING);
		    System.exit(1);
		}
		manager.update(child,aggregContent);
		if(myLibraClient.printEnable(aggregTNumber)) {
		    if(LibraTest.logLevel <= 850) myLibraClient.myPrint("updateChild( " + aggregTopic + ", " + child + ", " + aggregContent + ")", 850);
		}

	    }

	    // We do not have support for LibraTest.IMMEDIATEUPDATEPROP and ENABLEUPDATEACK, since we have to invoke the sendREfreshUpdateRoutine in a smart way so that aggregation of these updates can also be done in a single message. Just blindly caling it once for every topic will not be effective for aggregation of these related topics
	    
	    

	}
	

    }


    // This notifies us when we receive an update ack from the parent
    public void recvUpdateAck(Topic topic, NodeHandle parent,ScribeContent content) {
	if(content instanceof ESMAckContent) {
	    //System.out.println("recvUpdateAck() method called");
	    ESMAckContent mycontent = (ESMAckContent)content;
	    // We will update the lastAckTime
	    String topicName = mycontent.topicName;
	    int tNumber = myLibraClient.topicName2Number(topicName);
	    int treeStatus = myLibraClient.getTreeStatus(tNumber);
	    
	    if(!prevMetadata.containsKey(topic)) {
		if(LibraTest.logLevel <= 850) myLibraClient.myPrint("WARNING: Received unexpected UpdateAck for topic whose ESMContent is not present in prevMetadata", 850);
		
	    } else {
		//System.out.println("SysTime: " + System.currentTimeMillis() + " recvUpdateAck( " + topic + ", " + parent + ", " + mycontent + ")");
		ESMContent esmContent = (ESMContent)prevMetadata.get(topic);
		esmContent.setLastUpdateAckTime(System.currentTimeMillis());
	    }
	} else {
	    System.out.println("SysTime: " + System.currentTimeMillis() + " recvUpdateAck(UnknownContentType)");
	    System.exit(1);
	}
	
    }


    // This notifies us when we receive a failure for a anycast
    public void recvAnycastFail(Topic topic, NodeHandle failedAtNode, ScribeContent content) {
	if(content instanceof MyScribeContent) {
	    MyScribeContent myContent = (MyScribeContent)content;
	    myContent.addToMsgPath(myLibraClient.endpoint.getLocalNodeHandle(), myLibraClient.bindIndex, myLibraClient.jvmIndex, myLibraClient.vIndex);
	    MyScribeContent.NodeIndex[] traversedPath = myContent.getMsgPath();
	    String pathString = myLibraClient.pathAsString(traversedPath);
	    //System.out.println("anycastMessageContent at anycast(): " + myContent);
	    String topicName = myContent.topicName;
	    int topicNumber = myLibraClient.topicName2Number(topicName);
	    int seqNum = myContent.getSeq();
	    //if(LibraTest.logLevel <= 875) myLibraClient.myPrint("SysTime: " + System.currentTimeMillis() +  " ESMContentRunId: " + myContent.getESMRunId() + " Node "+myLibraClient.endpoint.getLocalNodeHandle()+" received ANYCASTFAILURE for Topic[ " + topicName + " ] " + topic + " Seq= " + seqNum  + " Src= " + failedAtNode + " ContentDetails: " + myContent, 875);
	    if(LibraTest.logLevel <= 875) myLibraClient.myPrint("SysTime: " + System.currentTimeMillis() +" Node received ANYCASTFAILURE for Topic[ " + topicName + " ] " + topic + " Src= " + failedAtNode + " ContentDetails: " + myContent + " TraversedPathHops: " + (traversedPath.length -1) + " TraversedPathString: " + pathString +  " )", 875);


	    myLibraClient.reqESMServerAnycastFailure(topicNumber, seqNum);
	} else if(content instanceof GrpMetadataRequestContent) {
	    GrpMetadataRequestContent myContent = (GrpMetadataRequestContent)content;
	    String topicName = myContent.topicName;
	    int topicNumber = myLibraClient.topicName2Number(topicName);
	    int seqNum = myContent.getSeq();
	     if(LibraTest.logLevel <= 875) myLibraClient.myPrint("SysTime: " + System.currentTimeMillis() +  " Node received GRPMETADATAREQUESTFAILURE for Topic[ " + topicName + " ] " + topic + " Src= " + failedAtNode + " ContentDetails: " + myContent, 875);
	}
    }



    // We could have different policies based on topicnames
    public int getPolicy(int streamId) {
	return ESMLIBRA;

    }
    



}

	

