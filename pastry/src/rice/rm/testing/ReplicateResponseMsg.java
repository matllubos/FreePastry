/*************************************************************************

"Free Pastry" Peer-to-Peer Application Development Substrate 

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


package rice.rm.testing;

import rice.pastry.*;
import rice.pastry.dist.*;

import rice.pastry.messaging.*;
import rice.pastry.security.*;

import rice.rm.*;

import java.util.*;
import java.io.*;

/**
 * @(#) ReplicateResponseMsg.java
 *
 *
 * @version $Id$
 * @author Animesh Nandi
 */
public class ReplicateResponseMsg extends TestMessage implements Serializable{


    private NodeSet replicaSet;

    private Id objectKey;

    // this timeout will be used only by the distributed test
    private static int TIMEOUT = 30*1000; 

    /**
     * Constructor : Builds a new RM Message
     */
    public ReplicateResponseMsg(NodeHandle source, Address address, Id _key, NodeSet _replicaSet, Credentials authorCred) {
	super(source,address, authorCred);
	this.replicaSet = _replicaSet;
	this.objectKey = _key;
    }



    /**
     * This method is called whenever the rm node receives a message for 
     * itself and wants to process it. The processing is delegated by rm 
     * to the message.
     * 
     */
    public void handleDeliverMessage( RMRegrTestApp testApp) {
	NodeSet set = getReplicaSet() ;
	Id key = getObjectKey();
	Object object = testApp.getPendingObject(key).getObject();


	//System.out.println(testApp.getNodeId() + "received response to replicatemsg"+ "replicaSet=" + set);
	// We will now send object insertion messages to each of these nodes 
	// and wait for the response messages

	for(int i=0; i<set.size(); i++) {
	    NodeHandle toNode;
	    InsertMsg msg;

	    toNode = set.get(i);
	    msg = new InsertMsg(testApp.getLocalHandle(),testApp.getAddress(), key, object, testApp.getCredentials());
	    testApp.route(null, msg, toNode);
	}
	

	if(testApp.getPastryNode() instanceof DistPastryNode) {
	    ReplicateTimeoutMsg msg;
	    msg = new ReplicateTimeoutMsg(testApp.getLocalHandle(),testApp.getAddress(), key, testApp.getCredentials());
	    testApp.getPastryNode().scheduleMsg(msg, TIMEOUT);
	}
	
    }


    
    public Id getObjectKey() {
	return objectKey;
    }

    public NodeSet getReplicaSet() {
	return replicaSet;
    }

}









