/*************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate

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

package rice.persistence;

/*
 * @(#) PersistenceManager.java
 *
 * @author Ansley Post
 * @author Alan Mislove
 * 
 * @version $Id$
 */
import java.io.*;
import java.util.*;

import rice.*;
import rice.p2p.commonapi.*;

/**
 * This class is an implementation of Storage which provides
 * in-memory storage. This class is specifically *NOT* designed
 * to provide persistent storage, and simply functions as an
 * enhanced hash table.
 */
public class MemoryStorage implements Storage {

  // the hashtable used to store the data
  private Hashtable storage;

  // the current list of Ids
  private IdSet idSet;
  
  // the current total size
  private int currentSize;

  // the factory for manipulating the ids
  private IdFactory factory;
  
  /**
   * Builds a MemoryStorage object.
   */
  public MemoryStorage(IdFactory factory) {
    this.factory = factory;
    idSet = factory.buildIdSet();
    storage = new Hashtable();
    currentSize = 0;
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
  public void store(Id id, Serializable obj, Continuation c) {
    if (id == null || obj == null) {
      c.receiveResult(new Boolean(false));
      return;
    }
    
    currentSize += getSize(obj);
    
    storage.put(id, obj);
    idSet.addId(id);
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
  public void unstore(Id id, Continuation c) {
    Object stored = storage.remove(id);
    idSet.removeId(id);

    if (stored != null) {
      currentSize -= getSize(stored);
      c.receiveResult(new Boolean(true));
    } else {
      c.receiveResult(new Boolean(false));
    }
  }

  public boolean exists(Id id) {
    return storage.containsKey(id);
  }

  public void exists(Id id, Continuation c) {
    c.receiveResult(new Boolean(storage.containsKey(id)));
  }

  public void getObject(Id id, Continuation c) {
    c.receiveResult(storage.get(id));
  }

  public void scan(IdRange range , Continuation c) {
    c.receiveResult(idSet.subSet(factory.buildIdRange(range.getCCWId(), range.getCWId())));    
  }

  /**
   * Return the objects identified by the given range of ids. The IdSet 
   * returned contains the Ids of the stored objects. The range is
   * partially inclusive, the lower range is inclusive, and the upper
   * exclusive.
   *
   *
   * NOTE: This method blocks so if the behavior of this method changes and
   * the guys don't fit in memory, this method may be deprecated.
   *
   * @param range The range to query  
   * @return The idset containg the keys 
   */
  public IdSet scan(IdRange range){
    IdSet toReturn = idSet.subSet(factory.buildIdRange(range.getCCWId(), range.getCWId())); 
    return(toReturn);
  }



  public void getTotalSize(Continuation c) {
    c.receiveResult(new Integer(currentSize));
  }

  /**
   * Returns the size of the given object, in bytes.
   *
   * @param obj The object to determine the size of
   * @return The size, in bytes
   */
  private int getSize(Object obj) {
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream oos = new ObjectOutputStream(baos);

      oos.writeObject(obj);
      oos.flush();

      return baos.toByteArray().length;
    } catch (IOException e) {
      throw new RuntimeException("Object " + obj + " was not serialized correctly!");
    }
  }
}
