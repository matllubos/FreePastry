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


package rice.rm.messaging;

import rice.pastry.*;
import rice.pastry.dist.*;

import rice.pastry.messaging.*;
import rice.pastry.security.*;

import rice.rm.*;

import java.util.*;
import java.io.*;

/**
 * @(#) RMReplicateResponseMsg.java
 *
 * A RM message. These messages are exchanged between the RM modules on the pastry nodes. 
 *
 * @version $Id$
 * @author Animesh Nandi
 */
public class RMReplicateResponseMsg extends RMMessage implements Serializable{


    private NodeSet replicaSet;

    private Id objectKey;

    // this timeout will be used only by the distributed test
    private static int TIMEOUT = 30*1000; 

    /**
     * Constructor : Builds a new RM Message
     */
    public RMReplicateResponseMsg(NodeHandle source, Address address, Id _key, NodeSet _replicaSet, Credentials authorCred, int seqno) {
	super(source,address, authorCred, seqno);
	this.replicaSet = _replicaSet;
	this.objectKey = _key;
    }



    /**
     * This method is called whenever the rm node receives a message for 
     * itself and wants to process it. The processing is delegated by rm 
     * to the message.
     * 
     */
    public void handleDeliverMessage( RMImpl rm) {
	NodeSet set = getReplicaSet() ;
	Id key = getObjectKey();
	Object object = rm.getPendingObject(key).getObject();


	//System.out.println(rm.getNodeId() + "received response to replicatemsg"+ "replicaSet=" + set);
	// We will now send object insertion messages to each of these nodes 
	// and wait for the response messages

	for(int i=0; i<set.size(); i++) {
	    NodeHandle toNode;
	    RMInsertMsg msg;

	    toNode = set.get(i);
	    msg = new RMInsertMsg(rm.getLocalHandle(),rm.getAddress(), key, object, rm.getCredentials(), rm.m_seqno ++);
	    rm.route(null, msg, toNode);
	}
	

	if(rm.getPastryNode() instanceof DistPastryNode) {
	    RMReplicateTimeoutMsg msg;
	    msg = new RMReplicateTimeoutMsg(rm.getLocalHandle(),rm.getAddress(), key, rm.getCredentials(), rm.m_seqno ++);
	    rm.getPastryNode().scheduleMsg(msg, TIMEOUT);
	}
	
    }


    
    public Id getObjectKey() {
	return objectKey;
    }

    public NodeSet getReplicaSet() {
	return replicaSet;
    }

}









