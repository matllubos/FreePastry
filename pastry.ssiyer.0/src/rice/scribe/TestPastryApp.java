package rice.scribe;

import rice.pastry.*;
import rice.pastry.direct.*;
import rice.pastry.standard.*;
import rice.pastry.join.*;
import rice.pastry.client.*;
import rice.pastry.messaging.*;
import rice.pastry.security.*;
import rice.pastry.routing.*;
import rice.pastry.testing.*;

import java.util.*;

/**
 * TestPastryApp
 *
 * a regression test suite for pastry.
 *
 * @author andrew ladd
 * @author peter druschel
 */

public class TestPastryApp extends PastryAppl {

    private static class MyAddress implements Address {
	private int myCode = 0x8aec747c;
	
	public int hashCode() { return myCode; }

	public boolean equals(Object obj) {
	    return (obj instanceof MyAddress);
	}
    }

    private static Credentials cred = new PermissiveCredentials();
    private static Address addr = new MyAddress();

    private class MyMessage extends Message
    {
	MyMessage( ) {
	    super( addr );
	}
    }

    public TestPastryApp( PastryNode pn, PastryRegrTest p ) {
	super( pn );
    }

    public TestPastryApp( PastryNode pn ) {
	super( pn );
    }
	
    public Address getAddress() { return addr; }

    public Credentials getCredentials() { return cred; }

    public PastryNode getPastryNode() { return thePastryNode; }

    public void sendMsg( NodeId targetId ) {
	routeMsg( targetId, new MyMessage(), cred, new SendOptions() );
    }

    public void sendMsgDirect( NodeHandle target ) {
	routeMsgDirect( target, new MyMessage(), cred, new SendOptions() );
    }


    public void messageForAppl( Message msg ) {
	System.out.println( "Node: " + getNodeId() + " in fn messageForApp" );
    }

    public boolean enrouteMessage( Message msg, NodeId target, 
				  NodeId nextHop, SendOptions opt ) {
	/*
	System.out.println( "Entering enrouteMessage in PastryAppl" );
	System.out.println( "At Node: " + getNodeId() );
	System.out.println( "Target: " + target );
	System.out.println( "NextHop: " + nextHop );
	System.out.println( "Leaving enrouteMessage in PastryAppl" );
	*/
	return true;
    }
    
    
}




