

package rice.rm.testing;

import rice.pastry.*;
import rice.pastry.messaging.*;
import rice.pastry.security.*;

import rice.rm.*;

import java.io.*;

/**
 * @(#) InsertMsg.java
 *
 * @version $Id$
 * @author Animesh Nandi
 */
public class InsertMsg extends TestMessage implements Serializable{


    private Id objectKey;

    /**
     * The object that needs to be inserted. 
     */
    private Object object;


    /**
     */
    public InsertMsg(NodeHandle source, Address address, Id _objectKey,Object _content, Credentials authorCred) {
	super(source,address, authorCred);
	this.object = _content;
	this.objectKey = _objectKey;
    }



    /**
     * This method is called whenever the rm node receives a message for 
     * itself and wants to process it. The processing is delegated by rm 
     * to the message.
     * 
     */
    public void handleDeliverMessage( RMRegrTestApp testApp) {
	Id objectKey;
	Object object;

	//System.out.println(testApp.getNodeId() + " received RMInsert msg ");
	// This is a local insert 
	objectKey = getObjectKey();
	object = getObject();
	testApp.store(objectKey, object);

	// We now send a Ack
	InsertResponseMsg msg;
	msg = new InsertResponseMsg(testApp.getLocalHandle(),testApp.getAddress(), objectKey, testApp.getCredentials());

	testApp.route(null, msg, getSource());
	
    }
    

    /**
     * Gets the objectKey of the object.
     * @return objectKey
     */
    public Id getObjectKey(){
	return objectKey;
    }
    


    /**
     * Gets the object contained in this message
     * @return object 
     */
    public Object getObject(){
	return object;
    }

    
}





