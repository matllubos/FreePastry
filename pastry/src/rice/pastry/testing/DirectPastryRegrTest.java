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
import rice.pastry.client.*;
import rice.pastry.messaging.*;
import rice.pastry.security.*;
import rice.pastry.routing.*;
import rice.pastry.leafset.*;

import java.util.*;

/**
 * PastryRegrTest
 *
 * a regression test suite for pastry.
 *
 * @version $Id$
 *
 * @author andrew ladd
 * @author peter druschel
 * @author sitaram iyer
 */

public class DirectPastryRegrTest extends PastryRegrTest {
    private NetworkSimulator simulator;

    /**
     * constructor
     */
    private DirectPastryRegrTest() {
	super();
	factory = new DirectPastryNodeFactory();
	simulator = ((DirectPastryNodeFactory)factory).getNetworkSimulator();
    }

    /**
     * lastElement, or returns null if empty.
     */
    protected NodeHandle getBootstrapHandle() {
	try {
	    PastryNode pn = (PastryNode) pastryNodes.lastElement();
	    return pn.getLocalHandle();
	} catch (NoSuchElementException e) {
	    return null;
	}
    }

    /**
     * send one simulated message
     */
    protected boolean simulate() { 
	boolean res = simulator.simulate(); 
	if (res) msgCount++;
	return res;
    }

    /**
     * get authoritative information about liveness of node.
     */
    protected boolean simIsAlive(NodeId id) {
	return simulator.isAlive(id);
    }

    /**
     * murder the node. comprehensively.
     */
    protected void killNode(PastryNode pn) {
	EuclideanNetwork enet = (EuclideanNetwork)simulator;
	enet.setAlive(pn.getNodeId(), false);
    }

    /**
     * main. just create the object and call PastryNode's main.
     */
    public static void main(String args[]) {
	DirectPastryRegrTest pt = new DirectPastryRegrTest();
	mainfunc(pt, args);
    }
}
