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

  // the hashtable used to store the data
  private Hashtable storage;
  
  /**
   * Builds a MemoryStorage object.
   */
  public MemoryStorage() {
    storage = new Hashtable();
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
  public void store(Comparable id, Serializable obj, Continuation c) {
    storage.put(id, obj);
    c.receiveResult(new Boolean(true));
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
  public void unstore(Comparable id, Continuation c) {
    c.receiveResult(new Boolean(storage.remove(id) != null));
  }

  public void exists(Comparable id, Continuation c) {
    c.receiveResult(new Boolean(storage.containsKey(id)));
  }

  public void getObject(Comparable id, Continuation c) {
    c.receiveResult(storage.get(id));
  }

  public void scan(Comparable start, Comparable end, Continuation c) {
    throw new UnsupportedOperationException("Method scan() is not yet implemented on MemoryStorage!.");
  }

  public void getTotalSize(Continuation c) {
    throw new UnsupportedOperationException("Method getTotalSize() is not yet implemented on MemoryStorage!.");
  }
}
