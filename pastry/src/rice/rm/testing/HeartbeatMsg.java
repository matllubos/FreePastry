

package rice.rm.testing;

import rice.pastry.*;
import rice.pastry.messaging.*;
import rice.pastry.security.*;
import rice.pastry.leafset.*;
import rice.pastry.routing.*;

import rice.rm.*;

import java.util.Vector;
import java.io.*;

/**
 * @(#) HeartbeatMsg.java
 *
 * Used for testing purposes.
 *
 * @version $Id$
 * @author Animesh Nandi
 */
public class HeartbeatMsg extends TestMessage implements Serializable{


    private Id objectKey;

    

    /**
     * Constructor : Builds a new Heartbeat Message
     * 
     */
    public HeartbeatMsg(NodeHandle source, Address address, Id _objectKey, Credentials authorCred) {
	super(source,address, authorCred);
	this.objectKey = _objectKey;
      
    }
    

    /**
     * This method is called whenever the rm node receives a message for 
     * itself and wants to process it. The processing is delegated by rm 
     * to the message.
     * 
     */
    public void 
	handleDeliverMessage( RMRegrTestApp testApp) {

	
	int replicaFactor;
	Id objectKey;

	NodeSet replicaSet;

	
	objectKey = getObjectKey();
	replicaFactor = RMRegrTestApp.rFactor;
	// replicaset includes this node also
	//System.out.println(testApp.getLeafSet());
	replicaSet = testApp.replicaSet(objectKey,replicaFactor + 1);
	
	// We send a Refresh message to each node in this set for this object
	for(int i=0; i<replicaSet.size(); i++) {
	    NodeHandle toNode;
	    RefreshMsg msg;

	    toNode = replicaSet.get(i);
	    msg = new RefreshMsg(testApp.getLocalHandle(),testApp.getAddress(), objectKey,testApp.getCredentials());
	    
	    testApp.route(null, msg, toNode);
	}
	
    }
    


    /**
     * Gets the objectKey of the object.
     * @return objectKey
     */
    public Id getObjectKey(){
	return objectKey;
    }
    

    
}





