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

package rice.p2p.past.gc;

import java.io.*;
import java.util.*;

import rice.*;
import rice.Continuation.*;
import rice.p2p.commonapi.*;
import rice.p2p.past.*;
import rice.p2p.past.messaging.*;
import rice.p2p.past.gc.messaging.*;
import rice.persistence.*;

/**
 * @(#) GCPastImpl.java
 * 
 * This class is an implementation of the GCPast interface, which provides
 * Past services with garbage collection.
 *
 * @version $Id$
 * @author Alan Mislove
 * @author Andreas Haeberlen
 */
public class GCPastImpl extends PastImpl implements GCPast {
  
  /**
   * The trash can, or where objects should go once expired.  If null, they are deleted
   */
  protected StorageManager trash;
  
  /**
   * The real factory, which is not wrapped with a GCIdFactory
   */
  protected IdFactory realFactory;
  
  /**
   * Constructor for GCPast
   *
   * @param node The node below this Past implementation
   * @param manager The storage manager to be used by Past
   * @param replicas The number of object replicas
   * @param instance The unique instance name of this Past
   * @param policy The policy this past instance should use
   * @param collectionInterval The frequency with which GCPast should collection local expired objects
   */
  public GCPastImpl(Node node, StorageManager manager, int replicas, String instance, PastPolicy policy, long collectionInterval) {
    this(node, manager, replicas, instance, policy, collectionInterval, null);
  }
  
  
  /**
   * Constructor for GCPast
   *
   * @param node The node below this Past implementation
   * @param manager The storage manager to be used by Past
   * @param replicas The number of object replicas
   * @param instance The unique instance name of this Past
   * @param trash The storage manager to place the deleted objects into (if null, they are removed)
   * @param policy The policy this past instance should use
   * @param collectionInterval The frequency with which GCPast should collection local expired objects
   */
  public GCPastImpl(Node node, StorageManager manager, int replicas, String instance, PastPolicy policy, long collectionInterval, StorageManager trash) {
    super(new GCNode(node), manager, replicas, instance, policy);
    this.trash = trash;
    this.realFactory = node.getIdFactory();
    
    endpoint.scheduleMessage(new GCCollectMessage(0, getLocalNodeHandle(), node.getId()), collectionInterval, collectionInterval);
  }
    
  /**
   * Inserts an object with the given ID into this instance of Past.
   * Asynchronously returns a PastException to command, if the
   * operation was unsuccessful.  If the operation was successful, a
   * Boolean[] is returned representing the responses from each of
   * the replicas which inserted the object.
   *
   * This method is equivalent to 
   *
   * insert(obj, INFINITY_EXPIRATION, command)
   *
   * as it inserts the object with a timeout value of infinity.  This
   * is done for simplicity, as well as backwards-compatibility for 
   * applications.
   * 
   * @param obj the object to be inserted
   * @param command Command to be performed when the result is received
   */
  public void insert(PastContent obj, Continuation command) {
    insert(obj, INFINITY_EXPIRATION, command); 
  }
  
  /**
   * Inserts an object with the given ID into this instance of Past.
   * Asynchronously returns a PastException to command, if the
   * operation was unsuccessful.  If the operation was successful, a
   * Boolean[] is returned representing the responses from each of
   * the replicas which inserted the object.
   *
   * The contract for this method is that the provided object will be 
   * stored until the provided expiration time.  Thus, if the application
   * determines that it is still interested in this object, it must refresh
   * the object via the refresh() method.
   * 
   * @param obj the object to be inserted
   * @param expiration the time until which the object must be stored
   * @param command Command to be performed when the result is received
   */
  public void insert(final PastContent obj, final long expiration, Continuation command) {
    doInsert(obj.getId(), new MessageBuilder() {
      public PastMessage buildMessage() {
        return new GCInsertMessage(getUID(), obj, expiration, getLocalNodeHandle(), obj.getId());
      }
    }, command);
  }

  /**
   * Updates the objects stored under the provided keys id to expire no
   * earlier than the provided expiration time.  Asyncroniously returns
   * the result to the caller via the provided continuation.  
   *
   * The result of this operation is an Object[], which is the same length
   * as the input array of Ids.  Each element in the array is either 
   * Boolean(true), representing that the refresh succeeded for the 
   * cooresponding Id, or an Exception describing why the refresh failed.  
   * Specifically, the possible exceptions which can be returned are:
   * 
   * ObjectNotFoundException - if no object was found under the given key
   * RefreshFailedException - if the refresh operation failed for any other
   *   reason (the getMessage() will describe the failure)
   * 
   * @param id The keys which to refresh
   * @param expiration The time to extend the lifetime to
   * @param command Command to be performed when the result is received
   */
  public void refresh(Id[] array, final long expiration, Continuation command) {
    IdSet set = realFactory.buildIdSet();
    for (int i=0; i<array.length; i++)
      set.addId(array[i]);
    
    refresh(set, expiration, command);
  }
  
  /**
   * Internal method which actually does the refreshing.  Should not be called
   * by external applications.
   *
   * @param ids The ids to refresh
   * @param expiration The time to extend the lifetime until
   * @param command The command to return the result to
   */
  protected void refresh(final IdSet ids, final long expiration, Continuation command) {
    if (ids.numElements() == 0) {
      command.receiveResult(new Boolean(true));
      return;
    }
    
    final Id[] array = ids.asArray();
    
    sendRequest(array[0], new GCLookupHandlesMessage(getUID(), array[0], getLocalNodeHandle(), array[0]), new StandardContinuation(command) {
      public void receiveResult(Object o) {
        NodeHandleSet set = (NodeHandleSet) o;
        final ReplicaMap map = new ReplicaMap();
        
        for (int i=0; i<array.length; i++) {
          NodeHandleSet replicas = endpoint.replicaSet(array[i], replicationFactor+1, set.getHandle(set.size()-1), set);
          
          if ((replicas != null) && (replicas.size() > 0)) {
            for (int j=0; j<replicas.size(); j++) 
              map.addReplica(replicas.getHandle(j), array[i]);
            
            ids.removeId(array[i]);
          }
        }
        
        final Iterator iterator = map.getReplicas();
        
        Continuation send = new StandardContinuation(parent) {
          public void receiveResult(Object o) {
            if (iterator.hasNext()) {
              NodeHandle next = (NodeHandle) iterator.next();
              IdSet ids = map.getIds(next);
              
              sendRequest(next, new GCRefreshMessage(getUID(), ids, expiration, getLocalNodeHandle(), next.getId()), this);
            } else {
              refresh(ids, expiration, parent);
            }
          }
        };
        
        send.receiveResult(null);
      }
    });
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
      super.deliver(id, message);
    } else {      
      if (msg instanceof GCInsertMessage) {
        final GCInsertMessage imsg = (GCInsertMessage) msg;        
        
        // make sure the policy allows the insert
        if (policy.allowInsert(imsg.getContent())) {
          storage.getObject(imsg.getContent().getId(), new StandardContinuation(getResponseContinuation(msg)) {
            public void receiveResult(Object o) {
              try {
                // allow the object to check the insert, and then insert the data
                PastContent content = imsg.getContent().checkInsert(imsg.getContent().getId(), (PastContent) o);
                storage.store(imsg.getContent().getId(), new GCPastMetadata(imsg.getExpiration()), content, parent);
              } catch (PastException e) {
                parent.receiveException(e);
              }
            }
          });
        } else {
          getResponseContinuation(msg).receiveResult(new Boolean(false));
        }
      } else if (msg instanceof GCRefreshMessage) {
        final GCRefreshMessage rmsg = (GCRefreshMessage) msg;        
        final GCPastMetadata metadata = new GCPastMetadata(rmsg.getExpiration());
        final Iterator i = rmsg.getKeys().getIterator();
        
        StandardContinuation process = new StandardContinuation(getResponseContinuation(msg)) {
          public void receiveResult(Object o) {
            if (i.hasNext()) {
              Id id = (Id) i.next();
              
              storage.setMetadata(id, metadata, this);
            } else {
              parent.receiveResult(Boolean.TRUE);
            }
          }
        };
        
        process.receiveResult(null);
      } else if (msg instanceof GCLookupHandlesMessage) {
        GCLookupHandlesMessage lmsg = (GCLookupHandlesMessage) msg;
        NodeHandleSet set = endpoint.replicaSet(lmsg.getId(), lmsg.getMax());
        set.removeHandle(getLocalNodeHandle().getId());
        set.putHandle(getLocalNodeHandle());
        
        log.finer("Returning replica set " + set + " for lookup handles of id " + lmsg.getId() + " max " + lmsg.getMax() + " at " + endpoint.getId());
        getResponseContinuation(msg).receiveResult(set);
      } else if (msg instanceof GCCollectMessage) {
        final Id[] array = scan().asArray();
        
      //  System.out.println("COLLECTING OBJECTS!!!!!!");
        
        Continuation remove = new ListenerContinuation("Removal of expired ids") {
          int index = -1;
          
          public void receiveResult(Object o) {
            while (++index < array.length) 
              if (((GCId) array[index]).getExpiration() < System.currentTimeMillis()) 
                break;
              
            if (index < array.length) {
              final GCId id = (GCId) array[index];

              if (trash != null) {                        
             //   System.out.println("MOVING " + id + " TO THE TRASH CAN!");

                storage.getObject(id.getId(), new StandardContinuation(this) {
                  public void receiveResult(Object o) {
                    trash.store(id.getId(), storage.getMetadata(id.getId()), (Serializable) o, new StandardContinuation(parent) {
                      public void receiveResult(Object o) {
                        storage.unstore(id.getId(), parent);
                      }
                    });
                  }
                });
              } else {
              //  System.out.println("DELETING " + id + "!");
                storage.unstore(id.getId(), this);
              }
            }
          }
        };
          
        remove.receiveResult(null);
      } else if (msg instanceof FetchHandleMessage) {
        final FetchHandleMessage fmsg = (FetchHandleMessage) msg;
        storage.getObject(fmsg.getId(), new StandardContinuation(getResponseContinuation(msg)) {
          public void receiveResult(Object o) {
            GCPastContent content = (GCPastContent) o;
            
            if (content != null) {
              log.fine("Retrieved data for fetch handles of id " + fmsg.getId());
              GCPastMetadata metadata = (GCPastMetadata) storage.getMetadata(fmsg.getId());
              
              if (metadata != null) 
                parent.receiveResult(content.getHandle(GCPastImpl.this, metadata.getExpiration()));
              else
                parent.receiveResult(content.getHandle(GCPastImpl.this, NO_EXPIRATION_SPECIFIED));
            } else {
              parent.receiveResult(null);
            }
          } 
        });
      } else {
        super.deliver(id, message);
      }
    }
  }
  
  // ---- REPLICATION MANAGER METHODS -----
  
  /**
   * This upcall is invoked to tell the client to fetch the given id, 
   * and to call the given command with the boolean result once the fetch
   * is completed.  The client *MUST* call the command at some point in the
   * future, as the manager waits for the command to return before continuing.
   *
   * @param id The id to fetch
   */
  public void fetch(final Id id, NodeHandle hint, Continuation command) {
    log.finer("Sending out replication fetch request for the id " + id);
    final GCId gcid = (GCId) id;
    
    if (gcid.getExpiration() < System.currentTimeMillis()) {
      command.receiveResult(Boolean.TRUE);
    } else if (storage.exists(gcid.getId())) {
      GCPastMetadata metadata = (GCPastMetadata) storage.getMetadata(gcid.getId());
      
      if ((metadata == null) || (metadata.getExpiration() < gcid.getExpiration())) 
        storage.setMetadata(gcid.getId(), new GCPastMetadata(gcid.getExpiration()), command);
      else
        command.receiveResult(Boolean.TRUE);
    } else {
      policy.fetch(gcid.getId(), hint, this, new StandardContinuation(command) {
        public void receiveResult(Object o) {
          if (o == null) {
            log.warning("Could not fetch id " + id + " - policy returned null in namespace " + instance);
            parent.receiveResult(new Boolean(false));
          } else {
            log.finest("inserting replica of id " + id);
            storage.getStorage().store(gcid.getId(), new GCPastMetadata(gcid.getExpiration()), (PastContent) o, parent);
          }
        }
      });
    }
  }
  
  /**
   * This upcall is to notify the client that the given id can be safely removed
   * from the storage.  The client may choose to perform advanced behavior, such
   * as caching the object, or may simply delete it.
   *
   * @param id The id to remove
   */
  public void remove(Id id, Continuation command) {
    storage.getStorage().unstore(((GCId) id).getId(), command);
  }
  
  /**
   * This upcall should return the set of keys that the application
   * currently stores in this range. Should return a empty IdSet (not null),
   * in the case that no keys belong to this range.
   *
   * @param range the requested range
   */
  public IdSet scan(IdRange range) {
    return buildGCIdSet(storage.getStorage().scan(range));
  }
  
  /**
   * This upcall should return the set of keys that the application
   * currently stores.  Should return a empty IdSet (not null),
   * in the case that no keys belong to this range.
   *
   * @param range the requested range
   */
  public IdSet scan() {
    return buildGCIdSet(storage.getStorage().scan());
  }
  
  /**
   * Internal method which builds a GCId set for use with replication
   *
   * @param set The set ot base it off of
   * @return The set
   */
  protected IdSet buildGCIdSet(IdSet set) {
    GCIdSet result = new GCIdSet();
    Iterator i = set.getIterator();
    
    while (i.hasNext()) {
      Id next = (Id) i.next();
      GCPastMetadata metadata = (GCPastMetadata) storage.getMetadata(next);
      
      if (metadata != null) 
        result.addId(new GCId(next, metadata.getExpiration()));
      else
        result.addId(new GCId(next, NO_EXPIRATION_SPECIFIED));
    }
    
    return result;
  }
  
  /**
  * This upcall should return whether or not the given id is currently stored
   * by the client.
   *
   * @param id The id in question
   * @return Whether or not the id exists
   */
  public boolean exists(Id id) {
    return storage.getStorage().exists(((GCId) id).getId());
  }  
  
  protected class ReplicaMap {
    protected HashMap map = new HashMap();
    public void addReplica(NodeHandle handle, Id id) {
      IdSet set = (IdSet) map.get(handle);
      
      if (set == null) {
        set = realFactory.buildIdSet();
        map.put(handle, set);
      }
      
      set.addId(id);
    }
    
    public Iterator getReplicas() {
      return map.keySet().iterator();
    }
    
    public IdSet getIds(NodeHandle replica) {
      return (IdSet) map.get(replica);
    }
  }
}

