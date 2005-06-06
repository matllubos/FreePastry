

package rice.rm.testing;

import rice.pastry.*;
import rice.pastry.messaging.*;
import rice.pastry.security.*;

import rice.rm.*;

import java.io.*;

/**
 *
 * @version $Id$
 * @author Animesh Nandi
 */
public class RefreshMsg extends TestMessage implements Serializable{


    private Id objectKey;


    /**
     */
    public RefreshMsg(NodeHandle source, Address address, Id _objectKey, Credentials authorCred) {
	super(source,address, authorCred);
	this.objectKey = _objectKey;
    }



    public void handleDeliverMessage( RMRegrTestApp testApp) {
	Id objectKey;
	// This is a local refresh 
	objectKey = getObjectKey();
	//System.out.println(testApp.getNodeId() + " received a Refresh msg for " + objectKey);
	testApp.refresh(objectKey);

    }
    

    /**
     * Gets the objectKey of the object.
     * @return objectKey
     */
    public Id getObjectKey(){
	return objectKey;
    }
    
}





