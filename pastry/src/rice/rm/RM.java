package rice.rm;

import rice.pastry.*;
import rice.pastry.direct.*;
import rice.pastry.standard.*;
import rice.pastry.join.*;
import rice.pastry.client.*;
import rice.pastry.security.*;
import rice.pastry.messaging.*;
import rice.pastry.routing.*;
import java.util.Hashtable;

import java.util.*;
import java.lang.*;

import rice.rm.messaging.*;

import ObjectWeb.Persistence.*;

/**
 * @(#) RM.java
 *
 * This interface is exported by Replica Manager for any applications which need to 
 * replicate objects across k closest nodes in the NodeId space.
 *
 * @version $Id$
 * @author Atul Singh
 * @author Animesh Nandi
 */
public interface RM {


     /**
     * Registers the application to the RM.
     * @param appAddress the application's address
     * @param replicaFactor the number of replicas this application needs for its objects
     * @return false if replicaFactor is greater than the permitted value(maxleafsetsize/2 + 1)
     *         else true.
     */
    public boolean Register(Address appAddress, int replicaFactor);


    /**
     * Called by applications when it needs to insert this object into k nodes 
     * closest to the objectKey. The k is the replicaFactor with which this application 
     * previously registered with RM.  The application should correctly call this
     * method in the sense that this pastryNode should CURRENTLY be closest node to the
     * objectKey in the nodeId space, otherwise insert returns false. When this call returns it is
     * not guaranteed that the object gets stored in all the replicas
     * instantaneously, what it simply does is issues requests to all those
     * concerned nodes to insert the object. For the time being, "object" should
     * implement ObjectWeb.Persistence.Persistable interface.
     *
     * @param appAddress applications address which calls this method
     * @param objectKey  the pastry key for the object
     * @param object the object (Currently, should implement Persistable)
     * @param authorCred the credentials of the author
     * @return true if operation successful else false
     */
    public boolean insert(Address appAddress, NodeId objectKey, Persistable object, Credentials authorCred);


    /**
     * Called by applications when it needs to delete this object from k nodes 
     * closest to the objectKey. The k is the replicaFactor with which this application 
     * previously registered with RM.  The application should correctly call this
     * method in the sense that this pastryNode should CURRENTLY be closest node to the
     * objectKey in the nodeId space, otherwise delete returns false. When this call 
     * returns it is not guaranteed that the object gets deleted in all the replicas
     * instantaneously, what it simply does is issues requests to all those
     * concerned nodes to delete the object.  
     *
     * @param appAddress applications address
     * @param objectKey  the pastry key for the object
     * @param authorCred the credentials of the author
     * @return true if operation successful
     */
    public boolean delete(Address appAddress, NodeId objectKey, Credentials authorCred);


    
    /**
     * Called by applications when it needs to update this object into k nodes 
     * closest to the objectKey. The k is the replicaFactor with which this application 
     * previously registered with RM. The application should correctly call this
     * method in the sense that this pastryNode should CURRENTLY be closest node to the
     * objectKey in the nodeId space, otherwise update returns false. When this 
     * call returns it is not guaranteed that the object gets Updated in all the replicas
     * instantaneously, what it simply does is issues requests to all those
     * concerned nodes to update the object. For the time being, "object" should
     * implement ObjectWeb.Persistence.Persistable interface.
     *
     * @param appAddress application's address
     * @param objectKey  the pastry key of the object
     * @param object the object (Currently, should implement Persistable)
     * @return true if operation successful
     */
    public boolean update(Address appAddress,NodeId objectKey, Persistable object);


}



