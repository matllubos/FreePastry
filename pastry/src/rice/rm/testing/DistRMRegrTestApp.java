
package rice.rm.testing;

import rice.pastry.*;
import rice.pastry.client.*;
import rice.pastry.security.*;
import rice.pastry.routing.*;
import rice.pastry.messaging.*;
import rice.pastry.direct.*;
import rice.pastry.leafset.*;

import rice.rm.*;
import rice.rm.messaging.*;
import rice.rm.testing.*;

import java.util.*;
import java.security.*;
import java.io.*;

/**
 * @(#) DistRMRegrTestApp.java
 *
 * an application used by the DistRMRegrTest  suite for Replica Manager
 * @version $Id$
 * @author Animesh Nandi 
 */

public class DistRMRegrTestApp extends RMRegrTestApp
{
    
    public boolean m_firstNodeInSystem;

    public static int insertionFreq = 5; // seconds
    
    public static int numObjectsInPeriod = 10; 

    public static int numObjects = 50;

    // Should not be very high, because the idea is that every 'checkFreq', we get an
    // approximate idea of the correct state of the system(desired position of keys)
    public static int refreshFreq = 5 ; 

    public static int refreshStart = 2 * DistRMRegrTestApp.insertionFreq * DistRMRegrTestApp.numObjects / DistRMRegrTestApp.numObjectsInPeriod;

    public static int checkFreq = DistRMRegrTestApp.refreshFreq * DistRMRegrTestApp.numObjects / DistRMRegrTestApp.numObjectsInPeriod;
    
    public static int checkStart = refreshStart;

    public int numReplicated = 0;

    public int numRefreshed = 0;

    public ScheduledMessage m_objectInsertionMsg;


    public DistRMRegrTestApp( PastryNode pn, Credentials cred , boolean firstNodeInSystem, String instance) {
	super(pn, cred, instance);
	m_firstNodeInSystem = firstNodeInSystem;

    }


    public void rmIsReady(RM rm) {
	RMImpl rmpl = (RMImpl)rm;
	System.out.println("I am up " + getNodeId());
	//System.out.println("MyRange= " + rmpl.myRange);
	if(m_firstNodeInSystem) {
	    
	    // Trigger the invokation of ObjectInsertion message
	     ObjectInsertionMsg insertionMsg;
	     insertionMsg = new ObjectInsertionMsg(getLocalHandle(), getAddress(), getCredentials());
	     m_objectInsertionMsg = m_pastryNode.scheduleMsgAtFixedRate(insertionMsg, insertionFreq *1000, insertionFreq *1000);

	     // Trigger the invokation of ObjectRefresh message
	     ObjectRefreshMsg refreshMsg;
	     refreshMsg = new ObjectRefreshMsg(getLocalHandle(), getAddress(), getCredentials());
	     m_pastryNode.scheduleMsgAtFixedRate(refreshMsg, refreshStart *1000, refreshFreq *1000);

	}
	
	
	// Trigger the periodic invokation of InvariantCheck message
	InvariantCheckMsg checkMsg;
	checkMsg = new InvariantCheckMsg(getLocalHandle(), getAddress(), getCredentials());
	m_pastryNode.scheduleMsgAtFixedRate(checkMsg, checkStart *1000 , checkFreq *1000);
    }
    

    
    public Id generateTopicId( String topicName ) { 
	MessageDigest md = null;
	
	try {
	    md = MessageDigest.getInstance( "SHA" );
	} catch ( NoSuchAlgorithmException e ) {
	    System.err.println( "No SHA support!" );
	}

	md.update( topicName.getBytes() );
	byte[] digest = md.digest();
	
	Id newId = NodeId.build( digest );
	
	return newId;
    }

}








