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

import rice.pastry.NodeId;
import rice.pastry.messaging.*;
import rice.pastry.security.*;

import java.util.Random;
import java.io.*;

/**
 * @(#) RMMessage.java
 *
 * A RM message. These messages are exchanged between the RM modules on the pastry nodes. 
 *
 * @version $Id$
 * @author Atul Singh
 * @author Animesh Nandi
 */
public class RMMessage extends Message implements Serializable{


    public static final int RM_INSERT = 1;

    public static final int RM_DELETE = 2;

    public static final int RM_REPLICATE = 3;

    public static final int RM_REMOVE = 4;

    public static final int RM_HEARTBEAT = 5;

    public static final int RM_REFRESH = 6;

    private NodeId _objectKey;

    /**
     * The object that needs to be inserted or updated. 
     * This is null for messages not required to contain the object.
     */
    private Object _object;

    /**
     * Replica Factor associated with the object.
     */
    private int _replicaFactor;

    /**
     * The type of the message.
     */
    protected int _messageType;

    /**
     * The credentials of the author for the object contained in this object
     */
    private Credentials _authorCred;
     
    private Address _rmdemuxAddress;


    /**
     * Constructor : Builds a new RM Message
     * @param address RM Application address
     * @param content the object to be inserted or updated, null for DELETE type of message
     * @param objectKey the pastry key of the object
     * @param messageType either RM_INSERT, RM_DELETE, RM_UPDATE
     * @param replicaFactor the number of replicas required for the object
     * 
     */
    public RMMessage(Address address, Object content, NodeId objectKey,int messageType, int replicaFactor, Address rmdemuxAddress, Credentials authorCred) {
	super(address);
	this._messageType = messageType;
	this._object = content;
	this._objectKey = objectKey;
	this._replicaFactor = replicaFactor;
	this._authorCred = authorCred;
	this._rmdemuxAddress = rmdemuxAddress;
    }
    

    /**
     * Gets the message type.
     * @return message type
     */
    public int getType() {
	return _messageType; 
    }


    /**
     * Sets the type of message.
     * @param messageType the type of message
     * @return void
     */
    public void setType(int messageType) { 
	_messageType = messageType; 
    }
    
    /**
     * Gets the objectKey of the object.
     * @return objectKey
     */
    public NodeId getobjectKey(){
	return _objectKey;
    }
    
    /**
     * Gets the replicaFactor of the object.
     * @return replicaFactor 
     */
    public int getreplicaFactor(){
	return _replicaFactor;
    }

    /**
     * Gets the author's credentials associated with this object
     * @return credentials
     */
    public Credentials getCredentials(){
	return _authorCred;
    }

    /**
     * Gets the object contained in this message
     * @return object 
     */
    public Object getObject(){
	return _object;
    }

    // This is the address for the demultiplexing by the Replica manager to the 
    // appropriate Replica Client
    public Address getAddress(){
	return _rmdemuxAddress;
    }
			      
    
}





