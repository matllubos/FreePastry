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

package rice.past;

import java.io.Serializable;

import rice.*;
import rice.pastry.security.Credentials;
import rice.p2p.commonapi.*;

/**
 * @(#) PASTPolicy.java
 * 
 * This interface is exported by policy objects provided by PAST
 * applications when they create a instance of PAST.  Provides methods
 * that allows PAST to check if various operations are allowed,
 * whether they have been initiated by a local client or a remote
 * node.
 * 
 * The policy object allows applications to control the semantics of
 * an instance of PAST. For instance, the policy controls which
 * objects can be inserted (e.g., content-hash objects only), what
 * happens when an object is inserted that already exists in PAST,
 * whether deletions are allowed and when, etc.
 *
 * @version $Id$
 * @author Peter Druschel 
 *
 */

public interface PASTPolicy {
  


    

    //
    // following are methods invoked on the nodes that store a given object replica
    //

  /**
   * Checks if a insert operation should be allowed.  Invoked when a
   * PAST node receives an insert request and it is a replica root for
   * the id.  This method also determines the effect of an insert
   * operation on an object that already exists: it computes the
   * new value of the stored object, as a function of the new and the
   * existing object.
   * 
   * @param id Pastry key identifying the object
   * @param newObj the new object to be stored
   * @param existingObj the existing object stored on this node (null if no object associated with id is stored on this node)
   * @param true if the local node is a replica root for the id, false otherwise
   * @param clCred credential object provided by the principal who initiated the operation
   * @return null, if the operation is not allowed; else, the object to be inserted 
   */
  public Serializable checkInsert(Id id, Serializable newObj, Serializable existingObject, boolean isRoot, Credentials clCred);

  /**
   * Checks if a lookup operation should be allowed.
   * Invoked when a PAST node receives an insert request and it is a replica root for the id, or it has the object.
   * 
   * @param id Pastry key identifying the object
   * @param existingObj the existing object stored on this node (null if no object associated with id is stored on this node)
   * @param true if the local node is a replica root for the id, false otherwise
   * @param clCred credential object provided by the principal who initiated the operation
   * @return null, if the operation is not allowed; else, the object to be returned to the client
   */
  public Serializable checkLookup(Id id, Serializable existingObject, boolean isRoot, Credentials clCred);

  /**
   * Checks if a exists operation should be allowed.
   * Invoked when a PAST node receives an insert request and it is a replica root for the id, or it has the object.
   * 
   * @param id Pastry key identifying the object
   * @param existingObj the existing object stored on this node (null if no object associated with id is stored on this node)
   * @param true if the local node is a replica root for the id, false otherwise
   * @param clCred credential object provided by the principal who initiated the operation
   * @param clCred credential object provided by the principal who initiated the operation
   * @return the result returned to the client
   */
  public boolean checkExists(Id id, Serializable existingObject, boolean isRoot, Credentials clCred);
  
  /**
   * Checks if a delete operation should be allowed.
   * Invoked when a PAST node receives an insert request and it is a replica root for the id, or it has the object.
   * 
   * @param id Pastry key identifying the object
   * @param existingObj the existing object stored on this node (null if no object associated with id is stored on this node)
   * @param true if the local node is a replica root for the id, false otherwise
   * @param clCred credential object provided by the principal who initiated the operation
   * @return true if the operation is allowed, false otherwise
   */
  public boolean checkDelete(Id id, Serializable existingObject, boolean isRoot, Credentials clCred);
  
}
