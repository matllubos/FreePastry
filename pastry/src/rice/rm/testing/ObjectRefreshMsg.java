

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


public class ObjectRefreshMsg extends TestMessage implements Serializable
{


    /**
     * Constructor
     */
    public ObjectRefreshMsg(NodeHandle source, Address address,  Credentials authorCred) {
	super(source, address, authorCred);
	
    }
    
    public void handleDeliverMessage( RMRegrTestApp _testApp) {
	/*
	DistRMRegrTestApp testApp = (DistRMRegrTestApp) _testApp; 
	System.out.println("ObjectRefresh message: at " + testApp.getNodeId());
	
	if(testApp.numRefreshed < DistRMRegrTestApp.numObjects) {
	    for(int i=0; i< DistRMRegrTestApp.numObjectsInPeriod; i++) {
		testApp.numRefreshed ++;
		String objName = "Object" + testApp.numRefreshed;
		Id objKey = testApp.generateTopicId(objName);
		testApp.heartbeat(objKey); 
	    }
	} 
	else {
	    testApp.numRefreshed = 0;
	}
	*/
	
    }

    public String toString() {
	return new String( "OBJECTREFRESH_MSG:" );
    }
}









