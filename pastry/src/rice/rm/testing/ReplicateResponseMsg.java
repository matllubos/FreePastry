

package rice.rm.testing;

import rice.pastry.*;
import rice.pastry.dist.*;

import rice.pastry.messaging.*;
import rice.pastry.security.*;

import rice.rm.*;

import java.util.*;
import java.io.*;

/**
 * @(#) ReplicateResponseMsg.java
 *
 *
 * @version $Id$
 * @author Animesh Nandi
 */
public class ReplicateResponseMsg extends TestMessage implements Serializable{


    private NodeSet replicaSet;

    private Id objectKey;

    // this timeout will be used only by the distributed test
    private static int TIMEOUT = 30*1000; 

    /**
     * Constructor : Builds a new RM Message
     */
    public ReplicateResponseMsg(NodeHandle source, Address address, Id _key, NodeSet _replicaSet, Credentials authorCred) {
	super(source,address, authorCred);
	this.replicaSet = _replicaSet;
	this.objectKey = _key;
    }



    /**
     * This method is called whenever the rm node receives a message for 
     * itself and wants to process it. The processing is delegated by rm 
     * to the message.
     * 
     */
    public void handleDeliverMessage( RMRegrTestApp testApp) {
	NodeSet set = getReplicaSet() ;
	Id key = getObjectKey();
	Object object = testApp.getPendingObject(key).getObject();


	//System.out.println(testApp.getNodeId() + "received response to replicatemsg"+ "replicaSet=" + set);
	// We will now send object insertion messages to each of these nodes 
	// and wait for the response messages

	for(int i=0; i<set.size(); i++) {
	    NodeHandle toNode;
	    InsertMsg msg;

	    toNode = set.get(i);
	    msg = new InsertMsg(testApp.getLocalHandle(),testApp.getAddress(), key, object, testApp.getCredentials());
	    testApp.route(null, msg, toNode);
	}
	

	if(testApp.getPastryNode() instanceof DistPastryNode) {
	    ReplicateTimeoutMsg msg;
	    msg = new ReplicateTimeoutMsg(testApp.getLocalHandle(),testApp.getAddress(), key, testApp.getCredentials());
	    testApp.getPastryNode().scheduleMsg(msg, TIMEOUT * 1000);
	}
	
    }


    
    public Id getObjectKey() {
	return objectKey;
    }

    public NodeSet getReplicaSet() {
	return replicaSet;
    }

}









