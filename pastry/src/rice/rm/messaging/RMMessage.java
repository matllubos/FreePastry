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

    /**
     * Notifies that the message is of type INSERT.
     */
    public static final int RM_INSERT = 1;

    /**
     * Notifies that the message is of type DELETE.
     */
    public static final int RM_DELETE = 2;

    /**
     * Notifies that the message is of type UPDATE.
     */
    public static final int RM_UPDATE = 3;


    /**
     * The pastry key of the object.
     */
    private NodeId _objectKey;

    /**
     * The object that needs to be inserted or updated. 
     * This is null for DELETE type of RM Message.
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
        
    /**
     * Constructor : Builds a new RM Message
     * @param address RM Application address
     * @param content the object to be inserted or updated, null for DELETE type of message
     * @param objectKey the pastry key of the object
     * @param messageType either RM_INSERT, RM_DELETE, RM_UPDATE
     * @param replicaFactor the number of replicas required for the object
     * 
     */
    public RMMessage(Address address, Object content, NodeId objectKey,int messageType, int replicaFactor, Credentials authorCred) {
	super(address);
	this._messageType = messageType;
	this._object = content;
	this._objectKey = objectKey;
	this._replicaFactor = replicaFactor;
	this._authorCred = authorCred;
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
			      
    
}



