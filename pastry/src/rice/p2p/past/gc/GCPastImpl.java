
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
  public static final boolean verbose = false;

  /**
   * The default expiration, or when objects inserted with no timeout will expire
   */
  public static final long DEFAULT_EXPIRATION = INFINITY_EXPIRATION;
  
  /**
   * The real factory, which is not wrapped with a GCIdFactory
   */
  protected IdFactory realFactory;
  
  // internal tracing stats
  public int collected = 0;
  public int refreshed = 0;
  
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
    this(node, manager, null, replicas, instance, policy, collectionInterval, null);
  }
  
  
  /**
   * Constructor for GCPast
   *
   * @param node The node below this Past implementation
   * @param manager The storage manager to be used by Past
   * @param backup The cache used for previously-responsible objects (can be null)
   * @param replicas The number of object replicas
   * @param instance The unique instance name of this Past
   * @param trash The storage manager to place the deleted objects into (if null, they are removed)
   * @param policy The policy this past instance should use
   * @param collectionInterval The frequency with which GCPast should collection local expired objects
   */
  public GCPastImpl(Node node, StorageManager manager, Cache backup, int replicas, String instance, PastPolicy policy, long collectionInterval, StorageManager trash) {
    super(new GCNode(node), manager, backup, replicas, instance, policy, trash);
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
    if (GCPastImpl.verbose) System.out.println("COUNT: " + System.currentTimeMillis() + " Inserting data of class " + obj.getClass().getName() + " under " + obj.getId().toStringFull());
    
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
  public void refresh(Id[] array, long expiration, Continuation command) {
    long[] expirations = new long[array.length];
    Arrays.fill(expirations, expiration);
    
    refresh(array, expirations, command);
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
  public void refresh(final Id[] array, long[] expirations, Continuation command) {
    if (GCPastImpl.verbose) System.out.println("COUNT: " + System.currentTimeMillis() + " Refreshing " + array.length + " data elements");

    GCIdSet set = new GCIdSet(realFactory);
    for (int i=0; i<array.length; i++)
      set.addId(new GCId(array[i], expirations[i]));
    
    refresh(set, new StandardContinuation(command) {
      public void receiveResult(Object o) {
        Object[] result = new Object[array.length];
        Arrays.fill(result, Boolean.TRUE);
        
        parent.receiveResult(result);
      }
    });
  }
  
  /**
   * Internal method which actually does the refreshing.  Should not be called
   * by external applications.
   *
   * @param ids The ids to refresh
   * @param expiration The time to extend the lifetime until
   * @param command The command to return the result to
   */
  protected void refresh(final GCIdSet ids, Continuation command) {
    System.out.println("REFRESH: CALLED WITH "+ ids.numElements() + " ELEMENTS");

    if (ids.numElements() == 0) {
      command.receiveResult(new Object[0]);
      return;
    }
    
    final Id[] array = ids.asArray();
    GCId start = (GCId) array[0];
    System.out.println("REFRESH: GETTINGS ALL HANDLES OF " + start);

    
    sendRequest(start.getId(), new GCLookupHandlesMessage(getUID(), start.getId(), getLocalNodeHandle(), start.getId()), 
                new NamedContinuation("GCLookupHandles for " + start.getId(), command) {
      public void receiveResult(Object o) {
        final NodeHandleSet set = (NodeHandleSet) o;
        final ReplicaMap map = new ReplicaMap();

        System.out.println("REFRESH: GOT " + set + " SET OF HANDLES!");
        
        endpoint.process(new Executable() {
          public Object execute() {
            System.out.println("REFRESH: ON PROCESSING THREAD!");

            for (int i=0; i<array.length; i++) {
              GCId id = (GCId) array[i];
              
              NodeHandleSet replicas = endpoint.replicaSet(id.getId(), replicationFactor+1, set.getHandle(set.size()-1), set);
              
              // if we have all of the replicas, go ahead and refresh this item
              if ((replicas != null) && ((replicas.size() == set.size()) || (replicas.size() == replicationFactor+1))) {
                for (int j=0; j<replicas.size(); j++) 
                  map.addReplica(replicas.getHandle(j), id);
                
                refreshed++;
                ids.removeId(id);
              }
            }
            
            System.out.println("REFRESH: DONE WITH PROCESSING THREAD - MOVING TO NORMAL THREAD!");
            
            return null;
          }
        }, new StandardContinuation(parent) {
          public void receiveResult(Object o) {
            System.out.println("REFRESH: BACK ON NORMAL THREAD!");

            final Iterator iterator = map.getReplicas();
            
            Continuation send = new StandardContinuation(parent) {
              public void receiveResult(Object o) {
                if (iterator.hasNext()) {
                  NodeHandle next = (NodeHandle) iterator.next();
                  GCIdSet ids = map.getIds(next);
                  System.out.println("REFRESH: SENDING REQUEST TO " + next + " FOR IDSET " + ids);
                  
                  
                  sendRequest(next, new GCRefreshMessage(getUID(), ids, getLocalNodeHandle(), next.getId()), 
                              new NamedContinuation("GCRefresh to " + next, this));
                } else {
                  System.out.println("REFRESH: DONE SENDING REQUESTS, RECURSING");
                  
                  refresh(ids, parent);
                }
              }
              
              public void receiveException(Exception e) {
                System.out.println("GOT EXCEPTION " + e + " REFRESHING ITEMS - CONTINUING");
                receiveResult(null);
              }
            };
            
            send.receiveResult(null);
          }
        });        
      }
    });
  }
  
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
    if (message.getMessage() instanceof GCLookupHandlesMessage) 
      return true;
    else
      return super.forward(message);
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
        inserts++;
        
        // make sure the policy allows the insert
        if (policy.allowInsert(imsg.getContent())) {
          storage.getObject(imsg.getContent().getId(), new StandardContinuation(getResponseContinuation(msg)) {
            public void receiveResult(Object o) {
              try {
                // allow the object to check the insert, and then insert the data
                GCPastContent content = (GCPastContent) imsg.getContent().checkInsert(imsg.getContent().getId(), (PastContent) o);
                storage.store(content.getId(), content.getMetadata(imsg.getExpiration()), content, parent);
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
        final Iterator i = rmsg.getKeys().getIterator();
        final Vector result = new Vector();
        other += rmsg.getKeys().numElements();
        
        StandardContinuation process = new StandardContinuation(getResponseContinuation(msg)) {
          public void receiveResult(Object o) {
            if (o != null)
              result.addElement(o);
            
            if (i.hasNext()) {
              final GCId id = (GCId) i.next();

              /* skip the object if we don't have it yet */
              if (storage.exists(id.getId())) {
                GCPastMetadata metadata = (GCPastMetadata) storage.getMetadata(id.getId());
                
                if (metadata != null) {
                  storage.setMetadata(id.getId(), metadata.setExpiration(id.getExpiration()), this);
                } else {
                  storage.getObject(id.getId(), new StandardContinuation(this) {
                    public void receiveResult(Object o) {
                      storage.setMetadata(id.getId(), ((GCPastContent) o).getMetadata(id.getExpiration()), parent);
                    }
                  });
                }
              } else {
                /* but first check and see if it's in the trash, so we can uncollect it */
                if (trash != null) {
                  trash.getObject(id.getId(), new StandardContinuation(this) {
                    public void receiveResult(Object o) {
                      if ((o != null) && (o instanceof GCPastContent)) {
                        System.out.println("GCREFRESH: Restoring object " + id + " from trash!");
                        GCPastContent content = (GCPastContent) o;
                        
                        storage.store(id.getId(), content.getMetadata(id.getExpiration()), content, new StandardContinuation(parent) {
                          public void receiveResult(Object o) {
                            trash.unstore(id.getId(), parent);
                          }
                        });
                      } else {
                        parent.receiveResult(Boolean.FALSE);
                      }
                    }
                  });
                } else {
                  receiveResult(Boolean.FALSE);
                }
              }
            } else {
              parent.receiveResult(result.toArray(new Boolean[0]));
            }
          }
        };
        
        process.receiveResult(null);
      } else if (msg instanceof GCLookupHandlesMessage) {
        GCLookupHandlesMessage lmsg = (GCLookupHandlesMessage) msg;
        NodeHandleSet set = endpoint.neighborSet(lmsg.getMax());
        set.removeHandle(getLocalNodeHandle().getId());
        set.putHandle(getLocalNodeHandle());
        
        log.finer("Returning neighbor set " + set + " for lookup handles of id " + lmsg.getId() + " max " + lmsg.getMax() + " at " + endpoint.getId());
        getResponseContinuation(msg).receiveResult(set);
      } else if (msg instanceof GCCollectMessage) {
        // get all ids which expiration before now
        collect(storage.scanMetadataValuesHead(new GCPastMetadata(System.currentTimeMillis())), new ListenerContinuation("Removal of expired ids") {
          public void receiveResult(Object o) {
            if (System.currentTimeMillis() > DEFAULT_EXPIRATION) 
              collect(storage.scanMetadataValuesNull(), new ListenerContinuation("Removal of default expired ids"));
          }
        });
      } else if (msg instanceof FetchHandleMessage) {
        final FetchHandleMessage fmsg = (FetchHandleMessage) msg;   
        fetchHandles++;
        
        storage.getObject(fmsg.getId(), new StandardContinuation(getResponseContinuation(msg)) {
          public void receiveResult(Object o) {
            GCPastContent content = (GCPastContent) o;
            
            if (content != null) {
              log.fine("Retrieved data for fetch handles of id " + fmsg.getId());
              GCPastMetadata metadata = (GCPastMetadata) storage.getMetadata(fmsg.getId());
              
              if (metadata != null) 
                parent.receiveResult(content.getHandle(GCPastImpl.this, metadata.getExpiration()));
              else
                parent.receiveResult(content.getHandle(GCPastImpl.this, DEFAULT_EXPIRATION));
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
  
  /**
   * Internal method which collects all of the objects in the given set
   * 
   * @param set THe set to collect
   * @param command The command to call once done
   */
  protected void collect(SortedMap map, Continuation command) {
    final Iterator i = map.keySet().iterator();  
    
    Continuation remove = new StandardContinuation(command) {          
      public void receiveResult(Object o) {
        if (i.hasNext()) {
          final Id gid = (Id) i.next();
          GCPastMetadata metadata = (GCPastMetadata) storage.getMetadata(gid);
          collected++;
          
          if (trash != null) {                        
            storage.getObject(gid, new StandardContinuation(this) {
              public void receiveResult(Object o) {
                if (o != null) {
                  trash.store(gid, storage.getMetadata(gid), (Serializable) o, new StandardContinuation(parent) {
                    public void receiveResult(Object o) {
                      storage.unstore(gid, parent);
                    }
                  });
                } else {
                  storage.unstore(gid, this);
                }
              }
            });
          } else {
            storage.unstore(gid, this);
          }
        } else {
          parent.receiveResult(Boolean.TRUE);
        }
      }
    };
    
    remove.receiveResult(null); 
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
      
      if (metadata == null) {
        storage.getObject(gcid.getId(), new StandardContinuation(command) {
          public void receiveResult(Object o) {
            GCPastContent content = (GCPastContent) o;
            storage.setMetadata(content.getId(), content.getMetadata(gcid.getExpiration()), parent);
          }
        });
      } else if (metadata.getExpiration() < gcid.getExpiration()) {
        storage.setMetadata(gcid.getId(), metadata.setExpiration(gcid.getExpiration()), command);
      } else {
        command.receiveResult(Boolean.TRUE);
      }
    } else {
      policy.fetch(gcid.getId(), hint, backup, this, new StandardContinuation(command) {
        public void receiveResult(Object o) {
          if (o == null) {
            log.warning("Could not fetch id " + id + " - policy returned null in namespace " + instance);
            parent.receiveResult(new Boolean(false));
          } else {
            GCPastContent content = (GCPastContent) o;
            log.finest("inserting replica of id " + id);
            
            storage.getStorage().store(gcid.getId(), content.getMetadata(gcid.getExpiration()), content, parent);
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
    super.remove(((GCId) id).getId(), command);
  }
  
  /**
   * This upcall should return the set of keys that the application
   * currently stores in this range. Should return a empty IdSet (not null),
   * in the case that no keys belong to this range.
   *
   * @param range the requested range
   */
  public IdSet scan(IdRange range) {
    GCIdRange gcRange = (GCIdRange) range;
    return new GCIdSet(storage.getStorage().scan(gcRange.getRange()), storage.getStorage().scanMetadata(gcRange.getRange()));
  }
  
  /**
   * This upcall should return the set of keys that the application
   * currently stores.  Should return a empty IdSet (not null),
   * in the case that no keys belong to this range.
   *
   * @param range the requested range
   */
  public IdSet scan() {
    return new GCIdSet(storage.getStorage().scan(), storage.getStorage().scanMetadata());
  }
  
  /**
   * This upcall should return whether or not the given id is currently stored
   * by the client.
   *
   * @param id The id in question
   * @return Whether or not the id exists
   */
  public boolean exists(Id id) {
    if (id instanceof GCId) 
      return storage.getStorage().exists(((GCId) id).getId());
    else
      return storage.getStorage().exists(id);
  }  
  
  protected class ReplicaMap {
    protected HashMap map = new HashMap();
    public void addReplica(NodeHandle handle, GCId id) {
      IdSet set = (IdSet) map.get(handle);
      
      if (set == null) {
        set = new GCIdSet(realFactory);
        map.put(handle, set);
      }
      
      set.addId(id);
    }
    
    public Iterator getReplicas() {
      return map.keySet().iterator();
    }
    
    public GCIdSet getIds(NodeHandle replica) {
      return (GCIdSet) map.get(replica);
    }
  }
}

