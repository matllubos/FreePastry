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

import rice.*;
import rice.p2p.commonapi.*;

/**
 * @(#) PAST.java
 * 
 * This interface is exported by all instances of PAST.  An instance
 * of PAST provides a distributed hash table (DHT) service.  Each
 * instance stores tuples consisting of a key and an object of a
 * particular type, which must implement the interface PASTContent.
 *  
 *
 * PAST is event-driven, so all methods are asynchronous
 * and receive their results using the command pattern.
 *
 * @version $Id$
 * @author Alan Mislove
 * @author Ansley Post
 * @author Peter Druschel
 *
 */

public interface PAST {
  
 /**
   * Inserts an object with the given ID into this instance of PAST.
   * Asynchronously returns a PASTException to command, if the
   * operation was unsuccessful.
   * 
   * @param obj the object to be inserted
   * @param command Command to be performed when the result is received */
 
  public void insert(PASTContent obj, Continuation command);

 
  /**
   * Retrieves the object stored in this instance of PAST with the
   * given ID.  Asynchronously returns a PASTContent object as the
   * result to the provided Continuation, or a PASTException. This
   * method is provided for convenience; its effect is identical to a
   * lookupHandles() and a subsequent fetch() to the handle that is
   * nearest in the network.
   * 
   * The client must authenticate the object. In case of failure, an
   * alternate replica of the object can be obtained via
   * lookupHandles() and fetch().
   * 
   * This method is not safe if the object is immutable and storage
   * nodes are not trusted. In this case, clients should used the
   * lookUpHandles method to obtains the handles of all primary
   * replicas and determine which replica is fresh in an
   * application-specific manner.
   *
   * @param id the key to be queried
   * @param command Command to be performed when the result is received */

  public void lookup(Id id, Continuation command);


  /**
   * Retrieves the handles of up to max replicas of the object stored
   * in this instance of PAST with the given ID.  Asynchronously
   * returns a Vector of PASTContentHandles as the result to the
   * provided Continuation, or a PASTException.  
   * 
   * Each replica handle is obtained from a different primary storage
   * root for the the given key. If max exceeds the replication factor
   * r of this PAST instance, only r replicas are returned.
   *
   * @param id the key to be queried
   * @param max the maximal number of replicas requested
   * @param command Command to be performed when the result is received 
   */

  public void lookupHandles(Id id, int max, Continuation command);
  

  /**
   * Retrieves the object associated with a given content handle.
   * Asynchronously returns a PASTContent object as the result to the
   * provided Continuation, or a PASTException.
   * 
   * The client must authenticate the object. In case of failure, an
   * alternate replica can be obtained using a different handle obtained via
   * lookupHandles().
   * 
   * @param id the key to be queried
   * @param command Command to be performed when the result is received 
   */

  public void fetch(PASTContentHandle id, Continuation command);


  /**
   * Return the ids of objects stored in this instance of PAST on the
   * *local* node, with ids in a given range. The IdSet returned
   * contains the Ids of the stored objects.
   *
   * @param range The range to query  
   * @return The set of ids
   */
    
  public IdSet scan(IdRange range);


  /**
   * get the nodeHandle of the local PAST node
   *
   * @return the nodehandle
   */
    
  public NodeHandle getNodeHandle();

}

