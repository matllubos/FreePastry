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
import java.util.logging.* ;

import rice.*;
import rice.Continuation.*;
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

  
  // ----- STATIC FIELDS -----

  // the number of milliseconds to wait before declaring a message lost
  public static int MESSAGE_TIMEOUT = 10000;
  

  // ----- VARIABLE FIELDS -----

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
  private Hashtable outstanding;

  // the factory for manipulating ids
  protected IdFactory factory;

  // the set of Ids which we need to fetch
  protected IdSet pending;

  // the logger which we will use
  protected Logger log = Logger.getLogger(this.getClass().getName());
  
  /**
   * Constructor for Past
   *
   * @param node The node below this Past implementation
   * @param manager The storage manager to be used by Past
   * @param replicas The number of object replicas
   * @param instance The unique instance name of this Past
   */
  public PastImpl(Node node, StorageManager manager, int replicas, String instance) {
    log.setLevel(Level.WARNING);
    storage = manager;
    endpoint = node.registerApplication(this, instance);
    factory = node.getIdFactory();

    id = Integer.MIN_VALUE;
    outstanding = new Hashtable();
    replicationFactor = replicas;
    pending = factory.buildIdSet();

  //  log.addHandler(new ConsoleHandler());
  //  log.setLevel(Level.FINEST);
  //  log.getHandlers()[0].setLevel(Level.FINEST);

    replicaManager = new RMImpl((rice.pastry.PastryNode) node, this, replicas, instance);
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
    log.finer("Getting the Continuation to respond to the message " + msg);
    final ContinuationMessage cmsg = (ContinuationMessage) msg;
    
    return new Continuation() {
      public void receiveResult(Object o) {
        cmsg.receiveResult(o);
        endpoint.route(msg.getSource().getId(), cmsg, msg.getSource());
      }

      public void receiveException(Exception e) {
        cmsg.receiveException(e);
        endpoint.route(msg.getSource().getId(), cmsg, msg.getSource());
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
    log.finer("Sending request message " + message + " to id " + id + " via " + hint);
    insertPending(message.getUID(), command);
    endpoint.scheduleMessage(new MessageLostMessage(message.getUID(), getLocalNodeHandle()), MESSAGE_TIMEOUT);
    endpoint.route(id, message, hint);
    
  }

  /**
   * Loads the provided continuation into the pending table
   *
   * @param uid The id of the message
   * @param command The continuation to run
   */
  private void insertPending(int uid, Continuation command) {
    log.finer("Loading continuation " + uid + " into pending table");
    outstanding.put(new Integer(uid), command);
  }

  /**
   * Removes and returns the provided continuation from the pending table
   *
   * @param uid The id of the message
   * @return The continuation to run
   */
  private Continuation removePending(int uid) {
    log.finer("Removing and returning continuation " + uid + " from pending table");
    return (Continuation) outstanding.remove(new Integer(uid));
  }

  /**
   * Handles the response message from a request.
   *
   * @param message The message that arrived
   */
  private void handleResponse(PastMessage message) {
    log.finer("handling reponse message " + message + " from the request");
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
  private void cache(final PastContent content) {
    log.finer("Inserting PastContent object " + content + " into cache");
    if ((content != null) && (! content.isMutable())) {
      
      storage.cache(content.getId(), content, new Continuation() {
        public void receiveResult(Object o) {
          if (! (o.equals(new Boolean(true)))) {
            log.warning("Caching of " + content + " failed.");
          }
        }

        public void receiveException(Exception e) {
          log.warning("Caching of " + content + " caused exception " + e);
        }
      });
    }
  }

  /**
   * Method which adds a set of Ids to the list of pending
   * ids to be fetched.
   *
   * @param set The keys to fetch
   */
  private void addToPending(IdSet set) {
    log.finer("Adding ids " + set + " to the list of pending");
    Iterator i = set.getIterator();

    while (i.hasNext()) {
      Id id = (Id) i.next();

      if ((! pending.isMemberId(id)) &&
          (! storage.getStorage().exists(id))) {
        pending.addId(id);
      }
    }
  }

  /**
   * Sends out the request for the next pending id. 
   */
  private void fetchNextPending() {
    final Id id = (Id) pending.getIterator().next();
    log.finer("Sending out request for the next pending id " + id);
    
    log.finest("inserting replica of id " + id);
    final Continuation receive = new Continuation() {
      public void receiveResult(Object o) {
        if (! (o.equals(new Boolean(true)))) {
          log.warning("Insertion of replica of id " + id + " failed.");
        }
        fetchPendingCompleted(id);
      }

      public void receiveException(Exception e) {
        log.warning("Insertion of replica id " + id + " caused exception " + e);
        fetchPendingCompleted(id);
      }
    };

    log.finest("retrieving replica id " + id);
    final Continuation insert = new Continuation() {
      public void receiveResult(Object o) {
        if (o == null) {
          log.warning("Could not fetch id " + id + " - replica returned null");
          fetchPendingCompleted(id);
        } else {
          PastContent content = (PastContent) o;
          storage.store(content.getId(), content, receive);
        }
      }

      public void receiveException(Exception e) {
        log.warning("Retrieval of replica id " + id + " caused exception " + e);
        fetchPendingCompleted(id);
      }
    };

    log.finest("fetching handles of replicas of id" + id);
    Continuation fetch = new Continuation() {
      public void receiveResult(Object o) {
        if (o != null) {
          PastContentHandle[] handles = (PastContentHandle[]) o;
          PastContentHandle handle = null;
          int i=0;

          while ((handle == null) && (i<handles.length)) {
            handle = handles[i];
            i++;
          }

          if (handle == null) {
            log.warning("Could not fetch object of id " + id + " - all replicas were null");
            fetchPendingCompleted(id);
          } else {
            fetch(handle, insert);
          }
        }
      }

      public void receiveException(Exception e) {
        log.warning("Fetch handles of replica of id " + id + " caused exception " + e + ".");
        fetchPendingCompleted(id);
      }
    };
    
    lookupHandles(id, replicationFactor, fetch);
  }

  /**
   * Method which is called once a fetch of a pending id has been completed, regardless
   * of failure or not.
   *
   * @param id The id which the fetch of has completed
   */
  private void fetchPendingCompleted(Id id) {
    log.finer("Fetch of pending id " + id + " has been completed");
    pending.removeId(id);

    if (pending.getIterator().hasNext()) {
      fetchNextPending();
    }
  }

  
  // ----- PAST METHODS -----
  
  /**
   * Inserts an object with the given ID into this instance of Past.
   * Asynchronously returns a PastException to command, if the
   * operation was unsuccessful.  If the operation was successful, a
   * Boolean[] is returned representing the responses from each of
   * the replicas which inserted the object.
   *
   * @param obj the object to be inserted
   * @param command Command to be performed when the result is received
   */
  public void insert(final PastContent obj, final Continuation command) {
    if (command == null) return;
    if (obj == null) {
      command.receiveException(new RuntimeException("Object cannot be null in insert!"));
      return;
    }
    
    log.fine("Inserting the object " + obj + " with the id " + obj.getId());

    replicaManager.registerKey((rice.pastry.Id) obj.getId());

    final Continuation receiveHandles = new Continuation() {

      // the number of handles we are waiting for
      private int num = -1;

      // the responses we have received
      private Vector handles = new Vector();

      public void receiveResult(Object o) {
        if (num == -1) {
          num = ((Integer) o).intValue();
        } else {
          log.finer("Received handle " + o + " for id " + obj.getId());
          handles.add(o);

          if (handles.size() == num) {
            Boolean[] array = new Boolean[num];

            for (int i=0; i<num; i++) {
              array[i] = (Boolean) handles.elementAt(i);
            }

            log.fine("Received all handles for id " + obj.getId() + " - returning result");
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
        log.finer("Received replicas " + replicas + " for id " + obj.getId());

        // record the number of handles we are going to insert
        receiveHandles.receiveResult(new Integer(replicas.size()));

        for (int i=0; i<replicas.size(); i++) {
          NodeHandle node = replicas.getHandle(i);
          sendRequest(node.getId(), new InsertMessage(getUID(), obj, getLocalNodeHandle(), obj.getId()), node, receiveHandles);
        }
      }

      public void receiveException(Exception e) {
        command.receiveException(e);
      }
    };

    sendRequest(obj.getId(), new LookupHandlesMessage(getUID(), obj.getId(), replicationFactor, getLocalNodeHandle(), obj.getId()), receiveReplicas);
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
  public void lookup(final Id id, final Continuation command) {
    log.fine("Looking up object stored in Past with id " + id);
    if (command == null) return;
    if (id == null) {
      command.receiveException(new RuntimeException("Id cannot be null in lookup!"));
      return;
    }

    storage.getObject(id, new StandardContinuation(command) {
      public void receiveResult(Object o) {
        if (o != null) {
          command.receiveResult(o);
        } else {
          // send the request across the wire, and see if the result is null or not
          sendRequest(id, new LookupMessage(getUID(), id, getLocalNodeHandle(), id), new StandardContinuation(this) {
            public void receiveResult(Object o) {
              // if we have an object, we return it
              // otherwise, we must check all replicas in order to make sure that
              // the object doesn't exist anywhere
              if (o != null) {
                command.receiveResult(o);
              } else {
                lookupHandles(id, replicationFactor, new StandardContinuation(this) {
                  public void receiveResult(Object o) {
                    PastContentHandle[] handles = (PastContentHandle[]) o;

                    for (int i=0; i<handles.length; i++) {
                      if (handles[i] != null) {
                        fetch(handles[i], command);
                        return;
                      }
                    }

                    // there were no replicas of the object
                    command.receiveResult(null);
                  }
                });
              }
            }
          });
        }
      }
    });
  }

  /**
   * Retrieves the handles of up to max replicas of the object stored
   * in this instance of Past with the given ID.  Asynchronously
   * returns an array of PastContentHandles as the result to the
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
    
    log.fine("Retrieving handles of up to " + max + " replicas of the object stored in Past with id " + id);

    if (max > replicationFactor)
      max = replicationFactor;

    final Continuation receiveHandles = new Continuation() {

      // the number of handles we are waiting for
      private int num = -1;

      // the handles we have received
      private Vector handles = new Vector();
      
      public void receiveResult(Object o) {
        if (num == -1) {
          num = ((Integer) o).intValue();
        } else {
          handles.add(o);
          log.finer("Receiving replica handle " + o + " for lookup Id " + id);

          if (handles.size() == num) {
            PastContentHandle[] array = new PastContentHandle[num];

            for (int i=0; i<num; i++) {
              array[i] = (PastContentHandle) handles.elementAt(i);
            }

            log.finer("Receiving all handles for lookup Id " + id + " - returning result");
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
        log.finer("Receiving replicas " + replicas + " for lookup Id " + id);

        // record the number of handles we are going to receive
        receiveHandles.receiveResult(new Integer(replicas.size()));

        for (int i=0; i<replicas.size(); i++) {
          NodeHandle node = replicas.getHandle(i);
          sendRequest(node.getId(), new FetchHandleMessage(getUID(), id, getLocalNodeHandle(), node.getId()), node, receiveHandles);
        }
      }

      public void receiveException(Exception e) {
        command.receiveException(e);
      }
    };

    sendRequest(id, new LookupHandlesMessage(getUID(), id, max, getLocalNodeHandle(), id), receiveReplicas);
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
    
    log.fine("Retrieving object associated with content handle " + handle);
    
    sendRequest(handle.getNodeHandle().getId(),
                new FetchMessage(getUID(), handle, getLocalNodeHandle(), handle.getNodeHandle().getId()),
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

  /**
   * Returns the number of replicas used in this Past
   *
   * @return the number of replicas for each object
   */
  public int getReplicationFactor() {
    return replicationFactor;
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
    log.fine("Forwarding given message " + message + " to the specified next hop");
    
    if (message.getMessage() instanceof LookupMessage) {
      final LookupMessage lmsg = (LookupMessage) message.getMessage();
      Id id = lmsg.getId();
      PastContent content = (PastContent) lmsg.getResponse();

      // if it is a request, look in the cache
      if (! lmsg.isResponse()) {
        log.finer("Lookup message " + lmsg + " is a request; look in the cache");
        if (storage.exists(id)) {
          // deliver the message, which will do what we want
          log.fine("Request for " + id + " satisfied locally - responding");
          deliver(endpoint.getId(), lmsg);
          
          return false;
        } 
      } else {
        // if the message hasn't been cached and we don't have it, cache it
        if ((! lmsg.isCached()) && (content != null) && (! content.isMutable())) {
          log.fine("Lookup for id " + id + " is being cached locally");
          lmsg.setCached();
          cache(content);
        }
      }
    }

    log.finest("Letting the message know that it was here");
    // let the message know that it was here
    if (message.getMessage() instanceof PastMessage) {
      ((PastMessage) message.getMessage()).addHop(getLocalNodeHandle());
    }

    return true;
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
      log.info("Received message " + message + " with destination " + id);
      
      if (msg instanceof InsertMessage) {
        final InsertMessage imsg = (InsertMessage) msg;
        pending.removeId(imsg.getContent().getId());
        
        log.fine("Received insert message with id " + imsg.getContent().getId());
        
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
        final LookupMessage lmsg = (LookupMessage) msg;
        
        // if the data is here, we send the reply, as well as push a cached copy
        // back to the previous node
        Continuation forward = new Continuation() {
          public void receiveResult(Object o) {

            // send result back
            getResponseContinuation(lmsg).receiveResult(o);

            // if possible, pushed copy into previous hop cache
            if ((lmsg.getPreviousNodeHandle() != null) &&
                (o != null) &&
                (! ((PastContent) o).isMutable())) {
              NodeHandle handle = lmsg.getPreviousNodeHandle();
              log.fine("Pushing cached copy of " + ((PastContent) o).getId() + " to " + handle);

              CacheMessage cmsg = new CacheMessage(getUID(), (PastContent) o, getLocalNodeHandle(), handle.getId());    
              endpoint.route(handle.getId(), cmsg, handle);
            }
          }

          public void receiveException(Exception e) {
            getResponseContinuation(lmsg).receiveException(e);
          }
        };
        
        // lookup the object
        storage.getObject(lmsg.getId(), forward);
      } else if (msg instanceof LookupHandlesMessage) {
        LookupHandlesMessage lmsg = (LookupHandlesMessage) msg;
        NodeHandleSet set = endpoint.replicaSet(lmsg.getId(), lmsg.getMax());
        log.finer("Returning replica set " + set + " for lookup handles of id " + lmsg.getId() + " max " + lmsg.getMax() + " at " + endpoint.getId());
        getResponseContinuation(msg).receiveResult(set);
      } else if (msg instanceof FetchMessage) {
        FetchMessage fmsg = (FetchMessage) msg;
        storage.getObject(fmsg.getHandle().getId(), getResponseContinuation(msg));
      } else if (msg instanceof FetchHandleMessage) {
        final FetchHandleMessage fmsg = (FetchHandleMessage) msg;
        storage.getObject(fmsg.getId(), new Continuation() {
          public void receiveResult(Object o) {
            PastContent content = (PastContent) o;

            if (content != null) {
              log.fine("Retrieved data for fetch handles of id " + fmsg.getId());
              getResponseContinuation(msg).receiveResult(content.getHandle(PastImpl.this));
            } else {
              getResponseContinuation(msg).receiveResult(null);
            }
          } 

          public void receiveException(Exception e) {
            getResponseContinuation(msg).receiveException(e);
          }
        });
      } else if (msg instanceof CacheMessage) {
        cache(((CacheMessage) msg).getContent());
      } else {
        log.severe("ERROR - Received message " + msg + "of unknown type.");
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
    log.fine("Got fetch for key set " + keySet);
    if (pending.getIterator().hasNext()) {
      addToPending(keySet);
    } else {
      addToPending(keySet);

      if (pending.getIterator().hasNext()) {
        fetchNextPending();
      }
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

    log.finer("Got new responsible range " + range);
    final Continuation c = new Continuation() {
      private Iterator notIds;

      public void receiveResult(Object o) {
        if (o instanceof IdSet) {
          Iterator i = ((IdSet) o).getIterator();
          Vector v = new Vector();
          while (i.hasNext()) v.add(i.next());
          notIds = v.iterator();
        } else if (! o.equals(new Boolean(true))) {
          log.warning("Unstore of id did not succeed!");
        }

        if (notIds.hasNext()) {
          final Id id = (Id) notIds.next();
          log.warning("Unstoring id " + id);

          storage.getStorage().getObject(id, new StandardContinuation(this) {
            public void receiveResult(Object o) {
              PastContent content = (PastContent) o;

              cache(content);
              storage.getStorage().unstore(id, parent);
            }
          });
        }
      }

      public void receiveException(Exception e) {
        log.warning("Exception " + e + " occured during removal of objects.");
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