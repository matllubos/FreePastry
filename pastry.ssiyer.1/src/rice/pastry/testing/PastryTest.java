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

import java.util.*;

/**
 * Pastry test.
 *
 * a simple test for pastry.
 *
 * @version $Id$
 *
 * @author andrew ladd
 */

public class PastryTest {
    private DirectPastryNodeFactory factory;
    private NetworkSimulator simulator;

    private Vector pastryNodes;
    private Vector pingClients;
    
    private Random rng;

    public PastryTest() {
	factory = new DirectPastryNodeFactory();
	simulator = factory.getNetworkSimulator();

	pastryNodes = new Vector();
	pingClients = new Vector();
	rng = new Random();
    }

    public void makePastryNode() {
	PastryNode pn = new PastryNode(factory);
	
	//System.out.println("created " + pn);

	pastryNodes.addElement(pn);
	
	PingClient pc = new PingClient(pn);

	pingClients.addElement(pc);

	int n = pastryNodes.size();

	if (n > 1) {
	    PastryNode other = (PastryNode) pastryNodes.get(n - 2);
	    
	    pn.receiveMessage(new InitiateJoin(other));
	}

	//System.out.println("");
    }

    public void sendPings(int k) {
	int n = pingClients.size();
		
	for (int i=0; i<k; i++) {
	    int from = rng.nextInt(n);
	    int to = rng.nextInt(n);
	    
	    PingClient pc = (PingClient) pingClients.get(from);
	    PastryNode pn = (PastryNode) pastryNodes.get(to);

	    pc.sendTrace(pn.getNodeId());

	    while(simulate());
	    
	    System.out.println("-------------------");
	}
    }

    public boolean simulate() { 
	return simulator.simulate(); 
    }

    public static void main(String args[]) {
	PastryTest pt = new PastryTest();
	
	int n = 4000;
	int m = 100;
	int k = 10;

	int msgCount = 0;

	Date old = new Date();

	for (int i=0; i<n; i++) {
	    pt.makePastryNode();
	    while (pt.simulate()) msgCount++;

	    if ((i + 1) % m == 0) {
		Date now = new Date();
		System.out.println((i + 1) + " " + (now.getTime() - old.getTime()) + " " + msgCount);
		msgCount = 0;
		old = now;
	    }
	}
	
	System.out.println(n + " nodes constructed");
	
	pt.sendPings(k);
    }
}
