

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


public class ObjectInsertionMsg extends TestMessage implements Serializable
{


    /**
     * Constructor
     */
    public ObjectInsertionMsg(NodeHandle source, Address address,  Credentials authorCred) {
	super(source, address, authorCred);
	
    }
    
    public void handleDeliverMessage( RMRegrTestApp _testApp) {
	DistRMRegrTestApp testApp = (DistRMRegrTestApp) _testApp; 
	System.out.println("ObjectInsertion message: at " + testApp.getNodeId());
	
	if(testApp.numReplicated < DistRMRegrTestApp.numObjects) {
	    for(int i=0; i< DistRMRegrTestApp.numObjectsInPeriod; i++) {
		testApp.numReplicated ++;
		String objName = "Object" + testApp.numReplicated;
		Id objKey = testApp.generateTopicId(objName);
		testApp.replicate(objKey); 
	    }
	} 
	else
	    testApp.m_objectInsertionMsg.cancel();
	
    }

    public String toString() {
	return new String( "OBJECTINSERTION_MSG:" );
    }
}









