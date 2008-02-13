



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
package rice.p2p.saar;

import java.util.*;
import java.lang.Double;
import rice.p2p.commonapi.*;
import rice.p2p.scribe.*;
import rice.p2p.scribe.ScribeImpl.TopicManager;
import rice.p2p.scribe.messaging.*;
import rice.environment.logging.Logger;


// This acts as a demultiplexor invoking the appropriate ScribePolicy
public class SaarPolicy extends rice.p2p.scribe.ScribePolicy.DefaultScribePolicy {

    protected SaarImpl saarImpl;

    private Random rng;


    public SaarPolicy(SaarImpl saarImpl) {
      super(saarImpl.getEnvironment());
	this.saarImpl = saarImpl;
	this.rng = new Random();
    }

    public boolean allowSubscribe(SubscribeMessage message, ScribeClient[] clients, NodeHandle[] children) {
	NodeHandle subscriber = (NodeHandle)message.getSubscriber();
	if(rice.p2p.saar.simulation.SaarSimTest.CENTRALIZEDSCRIBE) {
	    if(saarImpl.endpoint.getLocalNodeHandle().equals(saarImpl.centralizedHandle)) {
		return true;
	    } else {
		return false;
	    }
	} else {
	    // Normal tree formation

	    if(children.length <= (SaarClient.MAXCONTROLTREEFANOUT - 1)) {
		return true;
	    } else {
		if(SaarTest.logLevel <= 875) saarImpl.myPrint("Subscriber " + subscriber + " being pushed down to respect control tree MAXFANOUT: " + SaarClient.MAXCONTROLTREEFANOUT, 875);
		return false;
	    }
	}

    }

    
    public void childAdded(Topic topic, NodeHandle child) {
	// We can send this child the latest downwardpropagate content
	SaarImpl.SaarTopicManager sManager = (SaarImpl.SaarTopicManager) saarImpl.saarTopicManagers.get(topic);
	
	if(sManager.getLastDownwardPropagateContent() != null) {
	    saarImpl.endpoint.route(null,  new DownwardPropagateMessage(saarImpl.endpoint.getLocalNodeHandle(), topic, sManager.getLastDownwardPropagateContent()), child);

	}
	return;

    }
  
    public void childRemoved(Topic topic, NodeHandle child) {
      	if(saarImpl.saarTopicManagers.containsKey(topic)) {
	    SaarImpl.SaarTopicManager sManager = (SaarImpl.SaarTopicManager) saarImpl.saarTopicManagers.get(topic);
	    sManager.removeChildData(child);
	    if(SaarTest.logLevel <= 875) saarImpl.myPrint("SysTime: " + saarImpl.getCurrentTimeMillis() + " childRemoved( " + topic + ", " + child + ")", 875);
	}
    }

    public void updateChild(Topic topic, NodeHandle child, ScribeContent content) {
	if(content instanceof SaarContent) {
	    SaarContent mycontent = (SaarContent)content;
	    String topicName = mycontent.topicName;
	    int tNumber = mycontent.tNumber;
	    SaarImpl.SaarTopicManager sManager;
	    if(!saarImpl.saarTopicManagers.containsKey(topic)) {
		sManager = new SaarImpl.SaarTopicManager(saarImpl.myScribe,topic);
		saarImpl.saarTopicManagers.put(topic, sManager);
	    }
	    sManager = (SaarImpl.SaarTopicManager)saarImpl.saarTopicManagers.get(topic);
	    sManager.updateChildData(child,mycontent);
	    if(SaarTest.logLevel <= 875) saarImpl.myPrint("updateChild( " + topic + ", " + child + ", " + mycontent + ")", 875);
	}
	
    }


    public void recvUpdateAck(Topic topic, NodeHandle parent, ScribeContent content) {
	// We disabled the update acks, so we need not do anything
	return;
    }



    public void directAnycast(AnycastMessage message, NodeHandle parent, NodeHandle[] children) {
	if(message instanceof SubscribeMessage) {
	    
	    // This is needed only if allowSubscribe can return false, which happens when we enforce fanout caps in the control tree
	    // now randomize the children list
	    for (int i=0; i<children.length; i++) {
		int j = rng.nextInt(children.length);
		int k = rng.nextInt(children.length);
		NodeHandle tmp = children[j];
		children[j] = children[k];
		children[k] = tmp;
	    }
	    
	    for (int l=0; l<children.length; l++) {
		message.addFirst(children[l]);
	    }
	   
	    return;
	} 

	Topic topic = message.getTopic();
	SaarContent requestorContent = (SaarContent) message.getContent();
	SaarImpl.SaarTopicManager sManager = (SaarImpl.SaarTopicManager) saarImpl.saarTopicManagers.get(topic);
	if(SaarTest.logLevel <= 875) saarImpl.myPrint("SysTime: " + saarImpl.getCurrentTimeMillis() + " directAnycast(" + requestorContent.anycastGlobalId + ", parent= " + parent +  ")", 875);
	if(SaarTest.logLevel <= 850) saarImpl.myPrint("SysTime: " + saarImpl.getCurrentTimeMillis() + " directAnycast(" + message + " parent= " + parent + " children= [", 850);
	for(int i=0; i< children.length; i++) {
	    if(SaarTest.logLevel <=850) saarImpl.myPrint(children[i] + ",", 850);
	}
	if(SaarTest.logLevel <=850) saarImpl.myPrint("]", 850);

	// We add the parent first in the DFS anycast traversal 
	if (parent != null) {
	    SaarContent grpSummaryContent = sManager.getLastGrpSummaryContent();
	    if((grpSummaryContent == null) || grpSummaryContent.predicateSatisfied(requestorContent)) {
		message.addLast(parent);
	    } else {
		if(SaarTest.logLevel <=875) saarImpl.myPrint("Parent pruned from anycast traversal due to overall grp high utilization", 875);
	    }
	}

	// We will first randomize the children, this is crucial since many of the SaarContent whcih satisfy the boolean predicate might return '0'` in the compare operator
	for (int i=0; i<children.length; i++) {
	    int j = rng.nextInt(children.length);
	    int k = rng.nextInt(children.length);
	    NodeHandle tmp = children[j];
	    children[j] = children[k];
	    children[k] = tmp;
	}
	
	// We will now eliminate children whose SaarContent do not satisfy the predicate. For children having null SaarContent we assume it evalautes to 'true'
	Vector satisfiedNonNull = new Vector();
	Vector satisfiedNull = new Vector();
	for (int l = 0; l < children.length; l++) {
	    boolean toAdd = false;
	    NodeHandle handle = children[l];
	    if(sManager == null) {
		// We will add the child
		toAdd = true;
		satisfiedNull.add(handle);
	    } else {
		SaarContent sContent = sManager.getSaarContent(handle);
		if(sContent == null) {
		    toAdd = true;
		    satisfiedNull.add(handle);
		} else if(sContent.predicateSatisfied(requestorContent)) {
		    toAdd = true;
		    satisfiedNonNull.add(handle);
		} else {
		    toAdd = false;
		}
	    }
	}

	// We will now sort (simple maximal removal sort O(n^2) the 'satisfiedNonNull' list. The 'satisfiedNull' list is assumed to be sorted via randomization done at the beginning of this method.
	
	Vector sortedSatisfiedNonNull = new Vector();
	while(!satisfiedNonNull.isEmpty()) {
	    NodeHandle child;
	    SaarContent state;
	    NodeHandle chosenChild;
	    SaarContent chosenState;
	    
	    child = (NodeHandle)satisfiedNonNull.elementAt(0);
	    state = sManager.getSaarContent(child);
	    chosenChild = child;
	    chosenState = state;
	    for(int index = 1; index < satisfiedNonNull.size(); index++) {
		child = (NodeHandle)satisfiedNonNull.elementAt(index);
		state = sManager.getSaarContent(child);
		if(state.compare(requestorContent, chosenState) > 0) {
		    chosenChild = child;
		    chosenState = state;
		}
	    }
	    sortedSatisfiedNonNull.add(chosenChild);
	    satisfiedNonNull.remove(chosenChild);
	}
	
	// We combine the non-null/null lists to form one sorted list
	Vector sortedSatisfiedChildren = new Vector();
	for(int i=0; i < sortedSatisfiedNonNull.size(); i++) {
	    NodeHandle handle = (NodeHandle) sortedSatisfiedNonNull.elementAt(i);
	    sortedSatisfiedChildren.add(handle);
	}
	for(int i=0; i < satisfiedNull.size(); i++) {
	    NodeHandle handle = (NodeHandle) satisfiedNull.elementAt(i);
	    sortedSatisfiedChildren.add(handle);
	}

	// We will now add the children to the anycast traversal order. Note that the last node to be added using the anycastMessage.addFirst() should be the highest priority child/SaarContent and thus we add them in the reverse order
	for(int i=0; i < sortedSatisfiedChildren.size(); i++) {
	    NodeHandle handle = (NodeHandle) sortedSatisfiedChildren.elementAt(sortedSatisfiedChildren.size() - i -1);
			
			
	    // At this point we ensure that we do not make the anycast message traversal longer than MAXANYCASTWILLTRAVERSE, if this turns out the case we remove nodes from the end of the toVisit() since the nodes that are the lowest priority nodes in the anycast traversal
	    if(message.getToVisitSize() > 1 && (message.getVisitedSize() + message.getToVisitSize()) >= SaarImpl.MAXANYCASTWILLTRAVERSE) {
	      removeLastFromToVisit(message);
//		message.removeLastFromToVisit();
	    }		    
	    message.addFirst(handle);
	    // We also log the information of whom we chose and why
	    if(sManager != null) {

		if(SaarTest.logLevel <= 850) {
		    String choice = requestorContent.anycastGlobalId + " child: " + handle;
		    SaarContent sContent = sManager.getSaarContent(handle);
		    if(sContent !=null) {
			choice = choice + sContent;
		    } else {
			choice = choice + " NULL ";
		    }
		    if(SaarTest.logLevel <= 850) saarImpl.myPrint(choice, 850);	
		}

	    }
	}
	

    }

  public static void removeLastFromToVisit(AnycastMessage message) {
    message.remove(message.peekLastToVisit());    
  }

    public void recvAnycastFail(Topic topic, NodeHandle failedAtNode, ScribeContent content) {

	SaarContent mycontent = (SaarContent) content;
	// We will add the code of this node to track the path traversed
	mycontent.addToMsgPath(saarImpl.endpoint.getLocalNodeHandle(), saarImpl.bindIndex, saarImpl.jvmIndex, saarImpl.vIndex);

	saarImpl.getDataplaneClient(saarImpl.getTopic2Saartopic(topic)).recvAnycastFail(saarImpl.getTopic2Saartopic(topic), topic,failedAtNode,(SaarContent)content);

    }

    public void intermediateNode(ScribeMessage message) {
	if((message instanceof AnycastMessage) && !(message instanceof SubscribeMessage)) {
	    // We track the path of the anycast requests in the overlay
	    AnycastMessage aMessage = (AnycastMessage) message;
	    SaarContent mycontent = (SaarContent) aMessage.getContent();
	    // We will add the code of this node to track the path traversed
	    mycontent.addToMsgPath(saarImpl.endpoint.getLocalNodeHandle(), saarImpl.bindIndex, saarImpl.jvmIndex, saarImpl.vIndex);
	    
	}
    }

}

	

