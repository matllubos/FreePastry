/*************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate 

Copyright 2002, Rice University. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither  the name  of Rice  University (RICE) nor  the names  of its
contributors may be  used to endorse or promote  products derived from
this software without specific prior written permission.

This software is provided by RICE and the contributors on an "as is"
basis, without any representations or warranties of any kind, express
or implied including, but not limited to, representations or
warranties of non-infringement, merchantability or fitness for a
particular purpose. In no event shall RICE or contributors be liable
for any direct, indirect, incidental, special, exemplary, or
consequential damages (including, but not limited to, procurement of
substitute goods or services; loss of use, data, or profits; or
business interruption) however caused and on any theory of liability,
whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even
if advised of the possibility of such damage.

********************************************************************************/

package rice.pastry.testing;

import rice.pastry.*;
import rice.pastry.direct.*;
import rice.pastry.standard.*;
import rice.pastry.join.*;
import rice.pastry.routing.*;

import java.util.*;
import java.io.*;
import java.lang.*;

/**
 * SinglePingTest
 *
 * A performance test suite for pastry. 
 *
 * @version $Id$
 *
 * @author Rongmei Zhang
 */

public class SinglePingTest {
    private DirectPastryNodeFactory factory;
    private NetworkSimulator simulator;
    private TestRecord	testRecord;

    private Vector pastryNodes;
    private Vector pingClients;

    private Random rng;

    public SinglePingTest( TestRecord tr ) {
	simulator = new SphereNetwork();
	factory = new DirectPastryNodeFactory(new RandomNodeIdFactory(), simulator);
	simulator.setTestRecord( tr );
	testRecord = tr;

	pastryNodes = new Vector();
	pingClients = new Vector();
	rng = new Random(PastrySeed.getSeed());
    }
    private NodeHandle getBootstrap() {
	NodeHandle bootstrap = null;
	try {
	    PastryNode lastnode = (PastryNode) pastryNodes.lastElement();
            bootstrap = lastnode.getLocalHandle();
	} catch (NoSuchElementException e) {
	}
	return bootstrap;
    }

    public void makePastryNode() {
	PastryNode pn = factory.newNode(getBootstrap());
	pastryNodes.addElement(pn);
	
	Ping pc = new Ping(pn);
	pingClients.addElement(pc);
    }

    public void sendPings(int k) {
	int n = pingClients.size();
		
	for (int i=0; i<k; i++) {
	    int from = rng.nextInt(n);
	    int to = rng.nextInt(n);
	    
	    Ping pc = (Ping) pingClients.get(from);
	    PastryNode pn = (PastryNode) pastryNodes.get(to);

	    pc.sendPing(pn.getNodeId());
	    while(simulate());
	}
    }

    public boolean simulate() { 
	return simulator.simulate(); 
    }

    public void checkRoutingTable(){
	int 	i;
	Date 	prev = new Date();

        for ( i=0; i<testRecord.getNodeNumber(); i++) {
	    makePastryNode();
	    while (simulate());
	    if( i != 0 && i%1000 == 0 )
		System.out.println( i + " nodes constructed");
	}
	System.out.println( i + " nodes constructed");

	Date curr = new Date();
	long msec = curr.getTime()-prev.getTime();
	System.out.println( "time used " + (msec/60000) + ":" + ((msec%60000)/1000) + ":" + ((msec%60000)%1000) );

	//	simulator.checkRoutingTable();
    }

    public void test( ){
	int 	i;
	Date 	prev = new Date();

	System.out.println( "-------------------------" );
        for ( i=0; i<testRecord.getNodeNumber(); i++) {
	    makePastryNode();
	    while (simulate());
	    if( i != 0 && i%500 == 0 )
		System.out.println( i + " nodes constructed");
	}
	System.out.println( i + " nodes constructed");

	Date curr = new Date();
	long msec = curr.getTime()-prev.getTime();
	System.out.println( "time used " + (msec/60000) + ":" + ((msec%60000)/1000) + ":" + ((msec%60000)%1000) );
	prev = curr;

	sendPings( testRecord.getTestNumber() );
	System.out.println( testRecord.getTestNumber() + " lookups done" );

	curr = new Date();
	msec = curr.getTime()-prev.getTime();
	System.out.println( "time used " + (msec/60000) + ":" + ((msec%60000)/1000) + ":" + ((msec%60000)%1000) );

	testRecord.doneTest();
    }
}


