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
import rice.pastry.messaging.*;
import rice.pastry.security.*;
import rice.pastry.leafset.*;
import rice.pastry.routing.*;

import rice.rm.*;

import java.util.Vector;
import java.io.*;

/**
 * @(#) RMReplicate.java
 *
 * A RM message. These messages are exchanged between the RM modules on the pastry nodes. This message gets the list of Nodehandles which will be responsible
 * for storing the object. This set is obtained from the root of the object. 
 *
 * @version $Id$
 * @author Animesh Nandi
 */
public class RMReplicateMsg extends RMMessage implements Serializable{


    private Id objectKey;

    

    /**
     * Constructor : Builds a new RM Message
     * @param address RM Application address
     * @param content the object to be inserted or updated, null for DELETE type of message
     * @param objectKey the pastry key of the object
     * @param messageType either RM_INSERT, RM_DELETE, RM_UPDATE
     * @param replicaFactor the number of replicas required for the object
     * 
     */
    public RMReplicateMsg(NodeHandle source, Address address, Id _objectKey, Credentials authorCred, int seqno) {
	super(source,address, authorCred, seqno);
	this.objectKey = _objectKey;
      
    }
    

    /**
     * This method is called whenever the rm node receives a message for 
     * itself and wants to process it. The processing is delegated by rm 
     * to the message.
     * 
     */
    public void 
	handleDeliverMessage( RMImpl rm) {

	//System.out.println("RMReplicateMsg received");
	int replicaFactor;
	Id objectKey;


	// This a a message(asking for Replicating an object) that
	// was routed through pastry and delivered to this node
	// which is the closest to the objectKey
	
	NodeSet replicaSet;

	
	objectKey = getObjectKey();
	replicaFactor = rm.rFactor;
	//rm.app.store(objectKey, object);
	// replicaset includes this node also
	replicaSet = rm.replicaSet(objectKey,replicaFactor + 1);
	
	// We reply back with this NodeSet
       
	//System.out.println(rm.getNodeId() + "received replicatemsg");
	RMReplicateResponseMsg msg;
	msg = new RMReplicateResponseMsg(rm.getLocalHandle(),rm.getAddress(), objectKey, replicaSet, rm.getCredentials(), rm.m_seqno ++);

	rm.route(null, msg, getSource());
	
    }
    

    /**
     * Gets the objectKey of the object.
     * @return objectKey
     */
    public Id getObjectKey(){
	return objectKey;
    }
    

    
}





