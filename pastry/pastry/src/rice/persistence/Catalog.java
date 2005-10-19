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
 * @(#) Catalog.java
 *
 * @author Ansley Post
 * @author Alan Mislove
 * 
 * @version $Id$
 */
import java.io.*;

import rice.*;
import rice.p2p.commonapi.*;

/**
 * This interface is the abstraction of something which holds objects
 * which are available for lookup.  This interface does not, however,
 * specify how the objects are inserted, and makes NO guarantees as to
 * whether objects available at a given point in time will be available
 * at a later time.
 *
 * Implementations of the Catalog interface are designed to be interfaces
 * which specify how objects are inserted and stored, and are designed to
 * include a cache and persistent storage interface.
 */
public interface Catalog {

  /**
   * Returns whether or not an object is present in the location <code>id</code>.
   *
   * @param id The id of the object in question.
   * @return Whether or not an object is present at id.
   */
  public boolean exists(Id id);

  /**
   * Returns whether or not an object is present in the location <code>id</code>.
   * The result is returned via the receiveResult method on the provided
   * Continuation with an Boolean represnting the result.
   *
   * Returns <code>True</code> or <code>False</code> depending on whether the object
   * exists (through receiveResult on c);
   *
   * @param c The command to run once the operation is complete
   * @param id The id of the object in question.
   */
  public void exists(Id id, Continuation c);

  /**
   * Returns the object identified by the given id, or <code>null</code> if
   * there is no cooresponding object (through receiveResult on c).
   *
   * @param id The id of the object in question.
   * @param c The command to run once the operation is complete
   */
  public void getObject(Id id, Continuation c);

  /**
   * Return the objects identified by the given range of ids. The IdSet 
   * returned contains the Ids of the stored objects. The range is
   * partially inclusive, the lower range is inclusive, and the upper
   * exclusive.
   *
   * When the operation is complete, the receiveResult() method is called
   * on the provided continuation with a IdSet result containing the
   * resulting IDs.
   *
   * @param start The staring id of the range. (inclusive)
   * @param end The ending id of the range. (exclusive) 
   * @param c The command to run once the operation is complete
   */
  public void scan(IdRange range , Continuation c);

 /**
   * Return the objects identified by the given range of ids. The IdSet 
   * returned contains the Ids of the stored objects. The range is
   * partially inclusive, the lower range is inclusive, and the upper
   * exclusive.
   *
   * NOTE: This method blocks so if the behavior of this method changes and
   * no longer stored in memory, this method may be deprecated.
   *
   * @param range The range to query  
   * @return The idset containg the keys 
   */
  public IdSet scan(IdRange range);

  /**
   * Returns the total size of the stored data in bytes.The result
   * is returned via the receiveResult method on the provided
   * Continuation with an Integer representing the size.
   *
   * @param c The command to run once the operation is complete
   */
  public void getTotalSize(Continuation c);
}
