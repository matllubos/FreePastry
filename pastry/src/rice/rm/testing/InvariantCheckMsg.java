

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


public class InvariantCheckMsg extends TestMessage implements Serializable
{

    

    /**
     * Constructor
     */
    public InvariantCheckMsg(NodeHandle source, Address address,  Credentials authorCred) {
	super(source, address, authorCred);
	
    }
    
    public void handleDeliverMessage( RMRegrTestApp _testApp) {
	/*
	DistRMRegrTestApp testApp = (DistRMRegrTestApp) _testApp; 
	System.out.println("Invariant Check message: at " + testApp.getNodeId());
	testApp.checkPassed();
	testApp.clearRefreshedKeys();
	*/
    }

    public String toString() {
	return new String( "INVARIANTCHECK_MSG:" );
    }
}









