package rice.persistence;
/*
 * @(#) PersistenceManager.java
 *
 * @author Ansley Post
 * @author Alan Mislove
 */
import java.io.*;
import java.util.*;

import rice.*;
import rice.pastry.*;

/**
 * This class is an implementation of Storage which provides
 * in-memory storage. This class is specifically *NOT* designed
 * to provide persistent storage, and simply functions as an
 * enhanced hash table.
 */
public class MemoryStorage implements Storage {

  /**
   * Builds a MemoryStorage object.
   */
  public MemoryStorage() {
  }

  /**
   * Stores the object under the key <code>id</code>.  If there is already
   * an object under <code>id</code>, that object is replaced.
   *
   * This method completes by calling recieveResult() of the provided continuation
   * with the success or failure of the operation.
   *
   * @param obj The object to be made persistent.
   * @param id The object's id.
   * @param c The command to run once the operation is complete
   * @return <code>true</code> if the action succeeds, else
   * <code>false</code>.
   */
  public void store(NodeId id, Serializable obj, Continuation c) {
  }

  /**
   * Removes the object from the list of stored objects. If the object was not
   * in the cached list in the first place, nothing happens and <code>false</code>
   * is returned.
   *
   * This method completes by calling recieveResult() of the provided continuation
   * with the success or failure of the operation.
   *
   * @param id The object's persistence id
   * @param c The command to run once the operation is complete
   * @return <code>true</code> if the action succeeds, else
   * <code>false</code>.
   */
  public void unstore(NodeId id, Continuation c) {
  }

  /**
   * Returns whether or not an object is stored in the location <code>id</code>.
   *
   * @param id The id of the object in question.
   * @return Whether or not an object is stored at id.
   */
  public boolean isStored(NodeId id) {
    return false;
  }

  /**
   * Return the object identified by the given id.
   *
   * This method completes by calling recieveResult() of the provided continuation
   * with the result object.
   *
   * @param id The id of the object in question.
   * @param c The command to run once the operation is complete
   * @return The object, or <code>null</code> if the pid is invalid.
   */
  public void getObject(NodeId id, Continuation c) {
  }

  /**
   * Return the objects identified by the given range of ids.
   *
   * @param start The staring id of the range.
   * @param end The ending id of the range.
   * @return The nodeIds of objects stored within the given range.
   */
  public NodeId[] getObject(NodeId start, NodeId end) {
    return new NodeId[0];
  }

  /**
  * Returns the total size of the stored data in bytes.
   *
   * @return The total size, in bytes, of data stored.
   */
  public int getTotalSize() {
    return 0;
  }
}
