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

  // the current total size
  private int currentSize;
  
  /**
   * Builds a MemoryStorage object.
   */
  public MemoryStorage() {
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
  public void store(Comparable id, Serializable obj, Continuation c) {
    currentSize += getSize(obj);
    
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
    Object stored = storage.remove(id);

    if (stored != null) {
      currentSize -= getSize(stored);
      c.receiveResult(new Boolean(true));
    } else {
      c.receiveResult(new Boolean(false));
    }
  }

  public void exists(Comparable id, Continuation c) {
    c.receiveResult(new Boolean(storage.containsKey(id)));
  }

  public void getObject(Comparable id, Continuation c) {
    c.receiveResult(storage.get(id));
  }

  public void scan(Comparable start, Comparable end, Continuation c) {
    try {
      start.compareTo(end);
      end.compareTo(start);
    } catch (ClassCastException e) {
      c.receiveException(new IllegalArgumentException("start and end passed into scan are not co-comparable!"));
      return;
    }

    Vector result = new Vector();
    Iterator i = storage.keySet().iterator();

    while (i.hasNext()) {
      try {
        Comparable thisID = (Comparable) i.next();
        if ((start.compareTo(thisID) <= 0) &&
            (end.compareTo(thisID) >= 0))
          result.addElement(thisID);
      } catch (ClassCastException e) {
      }
    }

    Comparable[] array = new Comparable[result.size()];

    for (int j=0; j<result.size(); j++) {
      array[j] = (Comparable) result.elementAt(j);
    }

    c.receiveResult(array);    
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
