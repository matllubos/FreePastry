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

package rice.p2p.past;

import java.io.*;
import java.util.*;

import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.past.messaging.*;
import rice.persistence.*;

import rice.rm.*;

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

  // the factory for manipulating ids
  protected IdFactory factory;
  
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
    factory = node.getIdFactory();

    replicaManager = new RMImpl((rice.pastry.PastryNode) node, this, replicas, instance);
    id = Integer.MIN_VALUE;
    pending = new Hashtable();
    replicationFactor = replicas;
  }
  

  // ----- INTERNAL METHODS -----

  /**
   * Returns a new uid for a message
   *
   * @return A new id
   */
  private int getUID() {
    return id++;
  }

  /**
   * Returns a continuation which will respond to the given message.
   *
   * @return A new id
   */
  private Continuation getResponseContinuation(final PastMessage msg) {
    final ContinuationMessage cmsg = (ContinuationMessage) msg;
    
    return new Continuation() {
      public void receiveResult(Object o) {
        cmsg.receiveResult(o);
        endpoint.route(msg.getSource(), cmsg, null);
      }

      public void receiveException(Exception e) {
        cmsg.receiveException(e);
        endpoint.route(msg.getSource(), cmsg, null);
      }
    };
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
    sendRequest(id, message, null, command);
  }

  /**
   * Sends a request message across the wire, and stores the appropriate
   * continuation.  Sends the message using the provided handle as a hint.
   *
   * @param id The destination id
   * @param message The message to send.
   * @param handle The first hop hint
   * @param command The command to run once a result is received
   */
  private void sendRequest(Id id, PastMessage message, NodeHandle hint, Continuation command) {
    insertPending(message.getUID(), command);
    endpoint.route(id, message, hint);
  }

  /**
   * Loads the provided continuation into the pending table
   *
   * @param uid The id of the message
   * @param command The continuation to run
   */
  private void insertPending(int uid, Continuation command) {
    pending.put(new Integer(uid), command);
  }

  /**
   * Removes and returns the provided continuation from the pending table
   *
   * @param uid The id of the message
   * @return The continuation to run
   */
  private Continuation removePending(int uid) {
    return (Continuation) pending.remove(new Integer(uid));
  }

  /**
   * Handles the response message from a request.
   *
   * @param message The message that arrived
   */
  private void handleResponse(PastMessage message) {
    Continuation command = removePending(message.getUID());

    if (command != null) {
      message.returnResponse(command);
    } 
  }

  /**
   * Method which inserts the given object into the cache
   *
   * @param id The id
   * @param obj The object
   */
  private void cache(Id id, final PastContent content) {
    if ((content != null) && (! content.isMutable())) {
      
      storage.cache(id, content, new Continuation() {
        public void receiveResult(Object o) {
          if (! (o.equals(new Boolean(true)))) {
            System.out.println("Caching of " + content + " failed.");
          }
        }

        public void receiveException(Exception e) {
          System.out.println("Caching of " + content + " caused exception " + e + ".");
        }
      });
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
    if (command == null) return;
    if (obj == null) {
      command.receiveException(new RuntimeException("Object cannot be null in insert!"));
      return;
    }
                          
    sendRequest(obj.getId(), new InsertMessage(getUID(), obj, endpoint.getId(), obj.getId()), command);
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
    if (command == null) return;
    if (id == null) {
      command.receiveException(new RuntimeException("Id cannot be null in lookup!"));
      return;
    }
    
    sendRequest(id, new LookupMessage(getUID(), id, endpoint.getId(), id), command);
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
   * This method will return a PastContentHandle[] array containing all
   * of the handles.
   *
   * @param id the key to be queried
   * @param max the maximal number of replicas requested
   * @param command Command to be performed when the result is received
   */
  public void lookupHandles(final Id id, int max, final Continuation command) {
    if (command == null) return;
    if (id == null) {
      command.receiveException(new RuntimeException("Id cannot be null in lookupHandles!"));
      return;
    }
    if (max < 1)  {
      command.receiveException(new RuntimeException("Max must be positive in lookupHandles!"));
      return;
    }

    if (max > replicationFactor)
      max = replicationFactor;

    final Continuation receiveHandles = new Continuation() {

      private int num = -1;
      
      private Vector handles = new Vector();
      
      public void receiveResult(Object o) {
        if (num == -1) {
          num = ((Integer) o).intValue();
        } else {
          handles.add(o);

          if (handles.size() == num) {
            PastContentHandle[] array = new PastContentHandle[num];

            for (int i=0; i<num; i++) {
              array[i] = (PastContentHandle) handles.elementAt(i);
            }

            command.receiveResult(array);
          }
        }
      }

      public void receiveException(Exception e) {
        command.receiveException(e);
      }
    };

    Continuation receiveReplicas = new Continuation() {

      public void receiveResult(Object o) {
        NodeHandleSet replicas = (NodeHandleSet) o;

        receiveHandles.receiveResult(new Integer(replicas.size()));

        for (int i=0; i<replicas.size(); i++) {
          NodeHandle node = replicas.getHandle(i);
          sendRequest(null, new FetchHandleMessage(getUID(), id, endpoint.getId(), node.getId()), node, receiveHandles);
        }
      }

      public void receiveException(Exception e) {
        command.receiveException(e);
      }
    };

    sendRequest(id, new LookupHandlesMessage(getUID(), id, max, endpoint.getId(), id), receiveReplicas);
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
  public void fetch(PastContentHandle handle, Continuation command) {
    if (command == null) return;
    if (handle == null) {
      command.receiveException(new RuntimeException("Handle cannot be null in fetch!"));
      return;
    }
    
    sendRequest(null,
                new FetchMessage(getUID(), handle, endpoint.getId(), handle.getNodeHandle().getId()),
                handle.getNodeHandle(),
                command);
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

  /**
   * get the nodeHandle of the local Past node
   *
   * @return the nodehandle
   */
  public NodeHandle getLocalNodeHandle() {
    return endpoint.getLocalNodeHandle();
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
  public boolean forward(final RouteMessage message) {
    if (message.getMessage() instanceof LookupMessage) {
      LookupMessage lmsg = (LookupMessage) message.getMessage();
      Id id = lmsg.getId();
      PastContent content = (PastContent) lmsg.getResponse();
      
      cache(id, content);

      // if it is a request, look in the cache
      if (! lmsg.isResponse()) {
        if (storage.getCache().exists(id)) {
          storage.getCache().getObject(id, getResponseContinuation(lmsg));
          return false;
        }
      } else {
        cache(id, content);
      }

      return true;
    } else if (message.getMessage() instanceof FetchMessage) {
      FetchMessage fmsg = (FetchMessage) message.getMessage();
      Id id = fmsg.getHandle().getId();
      PastContent content = (PastContent) fmsg.getResponse();

      cache(id, content);
      return true;
    } else {
      return true;
    } 
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
        final InsertMessage imsg = (InsertMessage) msg;
        storage.getObject(imsg.getContent().getId(), new Continuation() {
          public void receiveResult(Object o) {
            try {
              PastContent content = imsg.getContent().checkInsert(imsg.getContent().getId(), (PastContent) o);
              storage.store(imsg.getContent().getId(), content, getResponseContinuation(msg));
            } catch (PastException e) {
              receiveException(e);
            }
          }

          public void receiveException(Exception e) {
            getResponseContinuation(msg).receiveException(e);
          }
        });
      } else if (msg instanceof LookupMessage) {
        LookupMessage lmsg = (LookupMessage) msg;
        storage.getObject(lmsg.getId(), getResponseContinuation(msg));
        // HERE  - NEED TO RETRIEVE HANDLE AND THEN VERIFY OBJECT
      } else if (msg instanceof LookupHandlesMessage) {
        LookupHandlesMessage lmsg = (LookupHandlesMessage) msg;
        getResponseContinuation(msg).receiveResult(endpoint.replicaSet(lmsg.getId(), lmsg.getMax()));
      } else if (msg instanceof FetchMessage) {
        FetchMessage fmsg = (FetchMessage) msg;
        storage.getObject(fmsg.getHandle().getId(), getResponseContinuation(msg));
        // HERE  - NEED TO VERIFY OBJECT
      } else if (msg instanceof FetchHandleMessage) {
        FetchHandleMessage fmsg = (FetchHandleMessage) msg;
        storage.getObject(fmsg.getId(), new Continuation() {
          public void receiveResult(Object o) {
            PastContent content = (PastContent) o;

            if (content != null) {
              getResponseContinuation(msg).receiveResult(content.getHandle(PastImpl.this));
            } else {
              getResponseContinuation(msg).receiveException(new RuntimeException("Replica did not have object!"));
            }
          }	

          public void receiveException(Exception e) {
            getResponseContinuation(msg).receiveException(e);
          }
        });
      } else {
        System.out.println("ERROR - Received message " + msg + " of unknown type.");
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
  public void fetch(rice.pastry.IdSet keySet) {
    Iterator i = keySet.getIterator();

    while (i.hasNext()) {
      final Id id = (Id) i.next();

      final Continuation receive = new Continuation() {
        public void receiveResult(Object o) {
          if (! (o.equals(new Boolean(true)))) {
            System.out.println("Insertion of replica id " + id + " failed.");
          }
        }

        public void receiveException(Exception e) {
          System.out.println("Insertion of replica id " + id + " caused exception " + e + ".");
        }
      };
      
      Continuation insert = new Continuation() {
        public void receiveResult(Object o) {
          storage.store(id, (Serializable) o, receive);
        }

        public void receiveException(Exception e) {
          System.out.println("Retreival of replica id " + id + " caused exception " + e + ".");
        }
      };

      lookup(id, insert);
    }
  }

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
  public void isResponsible(rice.pastry.IdRange range) {
    IdRange notRange = range.getComplementRange();

    Continuation c = new Continuation() {
      private Iterator notIds;

      public void receiveResult(Object o) {
        if (o instanceof IdSet) {
          notIds = ((IdSet) o).getIterator();
        } else if (! o.equals(new Boolean(true))) {
          System.out.println("Unstore of Id did not succeed!");
        }

        if (notIds.hasNext()) {
          storage.unstore((rice.pastry.Id) notIds.next(), this);
        }
      }

      public void receiveException(Exception e) {
        System.out.println("Exception " + e + " occurred during removal of objects.");
      }
    };

    storage.getStorage().scan((rice.pastry.IdRange) notRange, c);
  }

  /**
   * This upcall should return the set of keys that the application
   * currently stores in this range. Should return a empty IdSet (not null),
   * in the case that no keys belong to this range.
   * @param range the requested range
   */
  public rice.pastry.IdSet scan(rice.pastry.IdRange range) {
    return (rice.pastry.IdSet) storage.scan(range);
  }

  /**
   * Returns the replica manager for this Past instance.  Should *ONLY* be used
   * for testing.  Messing with this will cause unknown behavior.
   *
   * @return This Past's replica manager
   */
  public RM getReplicaManager() {
    return replicaManager;
  }


  // ----- UTILITY METHODS -----

  /**
   * Returns this Past's storage manager.
   *
   * @return This Past's storage manager.
   */
  public StorageManager getStorageManager() {
    return storage;
  }
}

