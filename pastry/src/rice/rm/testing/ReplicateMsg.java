

package rice.rm.testing;

import rice.pastry.*;
import rice.pastry.messaging.*;
import rice.pastry.security.*;
import rice.pastry.leafset.*;
import rice.pastry.routing.*;

import rice.rm.*;
import rice.rm.testing.*;

import java.util.Vector;
import java.io.*;

/**
 * @(#) Replicate.java
 *
 * @version $Id$
 * @author Animesh Nandi
 */
public class ReplicateMsg extends TestMessage implements Serializable{


    private Id objectKey;

    

    /**
     */
    public ReplicateMsg(NodeHandle source, Address address, Id _objectKey, Credentials authorCred) {
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

	//System.out.println("ReplicateMsg received");
	int replicaFactor;
	Id objectKey;


	// This a a message(asking for Replicating an object) that
	// was routed through pastry and delivered to this node
	// which is the closest to the objectKey
	
	NodeSet replicaSet;

	
	objectKey = getObjectKey();
	replicaFactor = RMRegrTestApp.rFactor;
	//rm.app.store(objectKey, object);
	// replicaset includes this node also
	replicaSet = testApp.replicaSet(objectKey,replicaFactor + 1);

	// We reply back with this NodeSet
       
	//System.out.println(rm.getNodeId() + "received replicatemsg");
	ReplicateResponseMsg msg;
	msg = new ReplicateResponseMsg(testApp.getLocalHandle(),testApp.getAddress(), objectKey, replicaSet, testApp.getCredentials());

	testApp.route(null, msg, getSource());
	
    }
    

    /**
     * Gets the objectKey of the object.
     * @return objectKey
     */
    public Id getObjectKey(){
	return objectKey;
    }
    
    
    
}





