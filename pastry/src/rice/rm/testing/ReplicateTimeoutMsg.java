

package rice.rm.testing;

import rice.pastry.*;
import rice.pastry.security.*;
import rice.pastry.routing.*;
import rice.pastry.messaging.*;

import rice.rm.*;
import rice.rm.messaging.*;

import java.io.*;
import java.util.*;

/**
 *
 * 
 * @version $Id$ 
 * 
 * @author Animesh Nandi
 */


public class ReplicateTimeoutMsg extends TestMessage implements Serializable
{

    private Id objectKey;
    

    /**
     * Constructor
     */
    public 
	ReplicateTimeoutMsg(NodeHandle source, Address address, Id _objectKey, Credentials authorCred) {
	super(source, address, authorCred);
	objectKey = _objectKey;
	
    }
    
    public void handleDeliverMessage( RMRegrTestApp testApp) {
	System.out.println("Timeout message: at " + testApp.getNodeId());
	Id key = getObjectKey();
	RMRegrTestApp.ReplicateEntry entry;

	entry = testApp.getPendingObject(key);
	if(entry == null) {
	    // It means that we received all the Acks before the timeout
	    return;
	}
	// On the assumption that atleast one node in the replicaSet
	// is a good node and replies within the Timeout period
	if(entry.getNumAcks()==0) {
	    // Notify application of failure
	    testApp.replicateSuccess(key,false);
	}
	else {
	    // Notify application of success
	    testApp.replicateSuccess(key,true);
	}
	// We also get rid of the state associated with this object from
	// the pendingObjectList
	testApp.removePendingObject(key);
    }
   

    public Id getObjectKey() {
	return objectKey;
    }


    public String toString() {
	return new String( "REPLICATE_TIMEOUT  MSG:" );
    }
}









