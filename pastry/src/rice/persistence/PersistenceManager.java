package rice.persistence;
/*
 * @(#) PersistenceManager.java
 *
 * @author Ansley Post
 */
import java.io.*;
import rice.pastry.*;

public interface PersistenceManager {

    /**
     * Makes the object persistent to disk and stored permanantly
     * 
     * If the object is already persistent, this method will
     * simply update the object's serialized image.
     *
     * @param obj The object to be made persistent.
     * @param pid The object's id.
     * @return <code>true</code> if the action succeeds, else
     * <code>false</code>.
     */
   public boolean persist(java.io.Serializable obj, NodeId id);

    /**
     * Request to remove the object from the list of persistend objects.
     * Delete the serialized image of the object from stable storage. If
     * necessary.
     *
     * <p> If the object was not in the cached list in the first place,
     * nothing happens and <code>false</code> is returned.
     *
     * @param pid The object's persistence id
     * @return <code>true</code> if the action succeeds, else
     * <code>false</code>.
     */
   public boolean unpersist(NodeId id);


    /**
     * Caches the object for potential future use
     * 
     * If the object is already persistent, this method will
     * simply update the object's serialized image.
     *
     * @param obj The object to be made persistent.
     * @param id The object's id.
     * @return <code>true</code> if the action succeeds, else
     * <code>false</code>.
     */
   public boolean cache(java.io.Serializable obj, NodeId id);

    /**
     * Request to remove the object from the list of cached objects.
     * Delete the serialized image of the object from stable storage. If
     * necessary.
     *
     * <p> If the object was not in the cached list in the first place,
     * nothing happens and <code>false</code> is returned.
     *
     * @param pid The object's persistence id
     * @return <code>true</code> if the action succeeds, else
     * <code>false</code>.
     */
   public boolean uncache(NodeId id);

   /**
    * Return the object identified by the given id.
    *
    * @param id The id of the object in question.
    * @return The object, or <code>null</code> if the pid is invalid.
    */
    public java.io.Serializable getObject(NodeId id);


   /**
    * Return the objects identified by the given range of ids 
    *
    * @param start The staring id of the range.
    * @param end The ending id of the range.
    * @return The objects, or <code>null</code> if there are no objects in 
    *  range .
    */
    public Vector getObject(NodeId start, NodeId end);
}
