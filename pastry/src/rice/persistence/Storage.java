package rice.persistence;
/*
 * @(#) Storage.java
 *
 * @author Ansley Post
 * @author Alan Mislove
 */
import java.io.*;

import rice.*;
import rice.pastry.*;

/**
 * This interface is the abstraction of something which provides a
 * local storage service, such as a persistence storage service or
 * an in-memory storage service.  Two implementations are provided,
 * the PersistentStorage and MemoryStorage, respsectively.
 */
public interface Storage {

  /**
   * Stores an object in this storage. This method is non-blocking.
   * If the object has already been stored at the location id, this
   * method has the effect of calling <code>unstore(id)</code> followed
   * by <code>store(id, obj)</code>. This method finishes by calling
   * receiveResult() on the provided continuation with the success
   * or failure of the store.
   *
   * @param id The object's id.
   * @param obj The object to store.
   * @param c The command to run once the operation is complete
   * @return <code>True</code> if the action succeeds, else
   * <code>False</code> (through receiveResult on c).
   */
  public void store(NodeId id, Serializable obj, Continuation c);

  /**
   * Removes the object from the list of stored objects. This method is
   * non-blocking. If the object was not in the stored list in the first place,
   * nothing happens and <code>False</code> is returned.
   *
   * @param pid The object's persistence id
   * @param c The command to run once the operation is complete
   * @return <code>true</code> if the action succeeds, else
   * <code>false</code>  (through receiveResult on c).
   */
  public void unstore(NodeId id, Continuation c);

  /**
   * Returns whether or not an object is stored in the location <code>id</code>.
   *
   * @param id The id of the object in question.
   * @return Whether or not an object is stored at id.
   */
  public boolean isStored(NodeId id);

  /**
   * Returns the object identified by the given id.
   *
   * @param id The id of the object in question.
   * @param c The command to run once the operation is complete
   * @return The object, or <code>null</code> if there is no cooresponding
   * object (through receiveResult on c).
   */
  public void getObject(NodeId id, Continuation c);

  /**
   * Return the objects identified by the given range of ids. The array
   * returned contains StorageObjects, with the NodeId and Serializable pairs.
   *
   * @param start The staring id of the range.
   * @param end The ending id of the range.
   * @return The objects
   */
  public NodeId[] getObject(NodeId start, NodeId end);

  /**
   * Returns the total size of the stored data in bytes.
   *
   * @return The total size, in bytes, of data stored.
   */
  public int getTotalSize();
}
