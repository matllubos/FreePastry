

package rice.rm.testing;

import rice.pastry.*;
import rice.pastry.messaging.*;
import rice.pastry.security.*;

import rice.rm.*;

import java.util.*;
import java.io.*;

/**
 * @(#) InsertResponseMsg.java
 *
 * @version $Id$
 * @author Animesh Nandi
 */
public class InsertResponseMsg extends TestMessage implements Serializable{


    private Id objectKey;


    /**
     * Constructor : Builds a new RM Message
     */
    public InsertResponseMsg(NodeHandle source, Address address, Id _key, Credentials authorCred) {
	super(source,address, authorCred);
	this.objectKey = _key;
    }



    /**
     * This method is called whenever the rm node receives a message for 
     * itself and wants to process it. The processing is delegated by rm 
     * to the message.
     * 
     */
    public void handleDeliverMessage( RMRegrTestApp testApp) {
	//System.out.println("Insert response msg");
	Id key = getObjectKey();
	RMRegrTestApp.ReplicateEntry entry = testApp.getPendingObject(key);
	entry.incNumAcks();
	if(entry.getNumAcks() == (RMRegrTestApp.rFactor + 1)) {
	    // This means we have got Acks from all the replica holders
	    testApp.replicateSuccess(key,true);
	    testApp.removePendingObject(key);
	}
    }



    public Id getObjectKey() {
	return objectKey;
    }


}









