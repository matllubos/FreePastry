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
 * @(#) PastImpl.java
 *
 * This is an implementation of the Past interface.
 *
 * @version $Id$
 * @author Alan Mislove
 * @author Ansley Post
 * @author Peter Druschel
 */
public class PastImpl implements Past, Application, RMClient {

  // this application's endpoint
  protected Endpoint endpoint;

  // the storage manager used by this Past
  protected StorageManager storage;

  // the replication factor for Past
  protected int replicationFactor;

  // the replica manager used by Past
  protected RM replicaManager;

  // the unique ids used by the messages sent across the wire
  private int id;

  // the hashtable of outstanding messages
  private Hashtable pending;
  
  /**
   * Constructor for Past
   *
   * @param node The node below this Past implementation
   * @param manager The storage manager to be used by Past
   * @param replicas The number of object replicas
   * @param instance The unique instance name of this Past
   */
  public PastImpl(Node node, StorageManager manager, int replicas, String instance) {
    storage = manager;
    endpoint = node.registerApplication(this, instance);

    replicaManager = new ReplicaManager((rice.pastry.PastryNode) node, this, replicas, instance);
    id = Integer.MIN_VALUE;
    pending = new Hashtable();
  }
  

  // ----- INTERNAL METHODS -----

  /**
   * Returns a new id for a message
   *
   * @return A new id
   */
  private int getId() {
    return id++;
  }

  /**
   * Sends a request message across the wire, and stores the appropriate
   * continuation.
   *
   * @param id The destination id
   * @param message The message to send.
   * @param command The command to run once a result is received
   */
  private void sendRequest(Id id, PastMessage message, Continuation command) {
    pending.put(new Integer(message.getUID()), command);
    endpoint.route(id, message, null);
  }

  /**
   * Handles the response message from a request.
   *
   * @param message The message that arrived
   */
  private void handleResponse(PastMessage message) {
    Continuation command = (Continuation) pending.remove(new Integer(message.getUID()));

    if (command != null) {
      command.receiveResult(message.getResult());
    }
  }

  
  // ----- PAST METHODS -----
  
  /**
   * Inserts an object with the given ID into this instance of Past.
   * Asynchronously returns a PastException to command, if the
   * operation was unsuccessful.
   *
   * @param obj the object to be inserted
   * @param command Command to be performed when the result is received
   */
  public void insert(PastContent obj, Continuation command) {
    sendRequest(obj.getId(), new InsertMessage(getId(), obj), command);
  }

  /**
   * Retrieves the object stored in this instance of Past with the
   * given ID.  Asynchronously returns a PastContent object as the
   * result to the provided Continuation, or a PastException. This
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
   * @param command Command to be performed when the result is received
   */
  public void lookup(Id id, Continuation command) {
    sendRequest(id, new LookupMessage(getId(), id), command);
  }

  /**
   * Retrieves the handles of up to max replicas of the object stored
   * in this instance of Past with the given ID.  Asynchronously
   * returns a Vector of PastContentHandles as the result to the
   * provided Continuation, or a PastException.
   *
   * Each replica handle is obtained from a different primary storage
   * root for the the given key. If max exceeds the replication factor
   * r of this Past instance, only r replicas are returned.
   *
   * @param id the key to be queried
   * @param max the maximal number of replicas requested
   * @param command Command to be performed when the result is received
   */
  public void lookupHandles(Id id, int max, Continuation command) {
  }

  /**
   * Retrieves the object associated with a given content handle.
   * Asynchronously returns a PastContent object as the result to the
   * provided Continuation, or a PastException.
   *
   * The client must authenticate the object. In case of failure, an
   * alternate replica can be obtained using a different handle obtained via
   * lookupHandles().
   *
   * @param id the key to be queried
   * @param command Command to be performed when the result is received
   */
  public void fetch(PastContentHandle id, Continuation command) {
    sendRequest(id.getId(), new FetchMessage(getId(), id), command);
  }

  /**
   * Return the ids of objects stored in this instance of Past on the
   * *local* node, with ids in a given range. The IdSet returned
   * contains the Ids of the stored objects.
   *
   * @param range The range to query
   * @return The set of ids
   */
  public IdSet scan(IdRange range) {
    return storage.scan(range);
  }
  

  // ----- COMMON API METHODS -----

  /**
   * This method is invoked on applications when the underlying node
   * is about to forward the given message with the provided target to
   * the specified next hop.  Applications can change the contents of
   * the message, specify a different nextHop (through re-routing), or
   * completely terminate the message.
   *
   * @param message The message being sent, containing an internal message
   * along with a destination key and nodeHandle next hop.
   *
   * @return Whether or not to forward the message further
   */
  public boolean forward(RouteMessage message) {
  }

  /**
   * This method is called on the application at the destination node
   * for the given id.
   *
   * @param id The destination id of the message
   * @param message The message being sent
   */
  public void deliver(Id id, Message message) {
    final PastMessage msg = (PastMessage) message;

    if (msg.isResponse()) {
      handleResponse((PastMessage) message);
    } else {
      if (msg instanceof InsertMessage) {
        InsertMessage imsg = (InsertMessage) msg;
        storage.store(imsg.getId(),
                      imsg.getContent(),
                      new Continuation() {
                        public void receiveResult(Object o) {
                          route(msg.getSource(), msg.getResponseMessage(o), null);
                        }

                        public void receiveException(Exception e) {
                          route(msg.getSource(), msg.getResponseMessage(e), null);
                        }
                      };);
      } else if (msg instanceof LookupMessage) {
        // lookup
      } else if (msg instanceof FetchMessage) {
        // fetch
      } else {
      }
    }
  }

  /**
   * This method is invoked to inform the application that the given node
   * has either joined or left the neighbor set of the local node, as the set
   * would be returned by the neighborSet call.
   *
   * @param handle The handle that has joined/left
   * @param joined Whether the node has joined or left
   */
  public void update(NodeHandle handle, boolean joined) {
  }

  
  // ----- REPLICA MANAGER METHODS -----

  /**
   * This upcall is invoked to notify the application that is should
   * fetch the cooresponding keys in this set, since the node is now
   * responsible for these keys also.
   *
   * @param keySet set containing the keys that needs to be fetched
   */
  public void fetch(IdSet keySet);

  /**
   * This upcall is simply to denote that the underlying replica manager
   * (rm) is ready. The 'rm' should henceforth be used by this RMClient
   * to issue the downcalls on the RM interface.
   *
   * @param rm the instance of the Replica Manager
   */
  public void rmIsReady(RM rm) {
  }

  /**
   * This upcall is to notify the application of the range of keys for
   * which it is responsible. The application might choose to react to
   * call by calling a scan(complement of this range) to the persistance
   * manager and get the keys for which it is not responsible and
   * call delete on the persistance manager for those objects.
   *
   * @param range the range of keys for which the local node is currently
   * responsible
   */
  public void isResponsible(IdRange range);

}

