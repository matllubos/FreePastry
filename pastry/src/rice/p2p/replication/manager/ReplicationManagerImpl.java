/*************************************************************************

"Free Pastry" Peer-to-Peer Application Development Substrate 

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

package rice.p2p.replication.manager;

import java.util.*;
import java.util.logging.*;

import rice.*;
import rice.Continuation.*;

import rice.p2p.commonapi.*;
import rice.p2p.replication.*;
import rice.p2p.replication.manager.messaging.*;

/**
 * @(#) ReplicationManagerImpl.java
 *
 * This class is the default provided implementation of the replication manager
 * used.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */
public class ReplicationManagerImpl implements ReplicationManager, ReplicationClient, Application {
  
  /**
   * The amount of time to wait between fetch calls to the client
   */
  public static int FETCH_DELAY = 500;
  
  /**
   * The amount of time to wait before giving up on a client fetch
   */
  public static int TIMEOUT_DELAY = 20000;
  
  /**
   * The id factory used for manipulating ids
   */
  protected IdFactory factory;
  
  /**
   * The endpoint used for sending reminder messages
   */
  protected Endpoint endpoint;
  
  /**
   * The replication used by the manager
   */
  protected ReplicationImpl replication;
  
  /**
   * The client of this manager
   */
  protected ReplicationManagerClient client;
  
  /**
   * The helper for the replication manager
   */
  protected ReplicationManagerHelper helper;
  
  /**
   * the logger which we will use
   */
  protected Logger log = Logger.getLogger(this.getClass().getName());
  
  /**
   * Constructor
   *
   * @param node The node below this Replication implementation
   * @param client The client for this Replication
   * @param replicationFactor The replication factor for this instance
   * @param instance The unique instance name of this Replication
   */
  public ReplicationManagerImpl(Node node, ReplicationManagerClient client, int replicationFactor, String instance) {
    this.client = client;
    this.factory = node.getIdFactory();
    this.endpoint = node.registerApplication(this, instance);
    this.helper = new ReplicationManagerHelper();
    
    log.finer(endpoint.getId() + ": Starting up ReplicationManagerImpl with client " + client);
    
//    log.addHandler(new ConsoleHandler());
//    log.setLevel(Level.FINER);
//    log.getHandlers()[0].setLevel(Level.FINER);
    
    this.replication = new ReplicationImpl(node, this, replicationFactor, instance);
  }
  
  
  // ----- INTERNAL METHODS -----
  
  /**
   * Internal method which clones an IdSet, so that iterators work as expected
   *
   * @param keySet The set to clone
   * @return The cloned set
   */
  protected IdSet clone(IdSet keySet) {
    IdSet result = factory.buildIdSet();
    Iterator i = keySet.getIterator();
    
    while (i.hasNext()) {
      result.addId((Id) i.next());
    }
    
    return result;
  }
  
  /**
   * Internal method which informs the client of the next id to fetch
   *
   * @param id The id which the client should fetch
   * @param uid The unique id for this message
   */
  protected void informClient(final Id id, final int uid) {
    log.fine(endpoint.getId() + ": Telling client to fetch id " + id);
    
    client.fetch(id, new Continuation() {
      public void receiveResult(Object o) {
        if (! (new Boolean(true)).equals(o)) {
          log.warning(endpoint.getId() + ": Fetching of id " + id + " failed with " + o);
        }
        
        log.fine(endpoint.getId() + ": Successfully fetched id " + id);
        
        helper.message(uid);
      }
      
      public void receiveException(Exception e) {
        receiveResult(e);
      }
    });
    
    endpoint.scheduleMessage(new TimeoutMessage(uid), TIMEOUT_DELAY);
  }
  
  /**
   * Internal method which schedules the next reminder message (if it is necessary),
   * or simply resets the active flag if there's nothing to be fetched.
   */
  protected void scheduleNext() {
    log.finer(endpoint.getId() + ": Scheduling next fetch in " + FETCH_DELAY + " milliseconds");
    
    endpoint.scheduleMessage(new ReminderMessage(), FETCH_DELAY);
  }
  
  
  // ----- REPLICATION METHODS -----
  
  /**
   * This upcall is invoked to notify the application that is should
   * fetch the cooresponding keys in this set, since the node is now
   * responsible for these keys also.
   *
   * @param keySet set containing the keys that needs to be fetched
   */
  public void fetch(IdSet keySet) {
    log.finer(endpoint.getId() + ": Adding keyset " + keySet + " to the list of pending ids");
    helper.fetch(keySet);
  }
  
  /**
   * This upcall is to notify the application of the range of keys for 
   * which it is responsible. The application might choose to react to 
   * call by calling a scan(complement of this range) to the persistance
   * manager and get the keys for which it is not responsible and
   * call delete on the persistance manager for those objects.
   *
   * @param range the range of keys for which the local node is currently 
   *              responsible  
   */
  public void setRange(IdRange range) {
    log.finer(endpoint.getId() + ": Removing range " + range + " from the list of pending ids");

    /* First, tell the helper that the range has changed */
    helper.setRange(range);
    
    IdRange notRange = range.getComplementRange();
    
    /* Next, we delete any unrelevant keys from the client */
    final Iterator i = clone(client.scan(notRange)).getIterator();

    Continuation c = new ListenerContinuation("Removal of Ids") {
      public void receiveResult(Object o) {
        if (! o.equals(new Boolean(true))) {
          log.warning(endpoint.getId() + ": Unstore of id did not succeed!");
        }
        
        if (i.hasNext()) {
          Id id = (Id) i.next();
          
          log.finer(endpoint.getId() + ": Telling client to delete id " + id);
          client.remove(id, this);
        }
      }
    };
  
    c.receiveResult(new Boolean(true));
  }    
  
  /**
   * This upcall should return the set of keys that the application
   * currently stores in this range. Should return a empty IdSet (not null),
   * in the case that no keys belong to this range.
   *
   * @param range the requested range
   */
  public IdSet scan(IdRange range) {
    return client.scan(range);
  }
  
  
  // ----- COMMONAPI METHODS -----
  
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
    if (message instanceof ReminderMessage) {
      log.finest(endpoint.getId() + ": Received reminder message");
      helper.wakeup();
    } else if (message instanceof TimeoutMessage) {
      log.finest(endpoint.getId() + ": Received timeout message");
      helper.message(((TimeoutMessage) message).getUID());
    } else {
      log.warning(endpoint.getId() + ": Received unknown message " + message);
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
  
  // ----- UTILITY METHODS -----
  
  /**
   * Utility method which returns the underlying replication object.  Should only
   * be used for testing - messing with this causes undefined behavior.
   *
   * @return The underlying replication object
   */
  public ReplicationImpl getReplication() {
    return replication;
  }
  
  /**
   * Inner class which keeps track of the state we're in- waiting, sleeping, or with
   * nothing to do.
   */
  protected class ReplicationManagerHelper {
    
    /**
     * The set of possible states we can be in
     */
    public int STATE_NOTHING = 0;
    public int STATE_WAITING = 1;
    public int STATE_SLEEPING = 2;
    
    /**
     * The current state that we are in
     */
    protected int state;
    
    /**
     * The set of keys we have yet to fetch
     */
    protected IdSet set;
    
    /**
     * The next message UID which is available
     */
    protected int nextUID;
    
    /**
     * Constructor 
     */
    public ReplicationManagerHelper() {
      set = factory.buildIdSet();
      state = STATE_NOTHING;
      nextUID = Integer.MIN_VALUE;
    }
    
    /**
     * Method by which keys are added to the list of keys to fetch
     *
     * @param keySet The keys to add
     */
    public synchronized void fetch(IdSet keySet) {
      Iterator i = keySet.getIterator();

      while (i.hasNext()) {
        Id id = (Id) i.next();
        
        if (! (set.isMemberId(id) || client.exists(id))) {
          set.addId(id);
        }
      }
        
      if (state == STATE_NOTHING) {
        send();
      }
    }
    
    /**
     * Method by which the range is set, which will delete any keys
     * from the to fetch list not in the range
     *
     * @param range The new range
     */
    public synchronized void setRange(IdRange range) {
      IdRange notRange = range.getComplementRange();
      
      /* first, we remove any non-relevant keys from the list of pending keys */
      Iterator i = ReplicationManagerImpl.this.clone(set).getIterator();
      
      /* now look for any matching ids */
      while (i.hasNext()) {
        Id id = (Id) i.next();
        
        if (range.containsId(id)) {
          set.removeId(id);
        }
      }
    }
    
    /**
     * Method which determines if a message should be sent, and if so, sends it
     */
    protected synchronized void send() {
      if ((state != STATE_WAITING) && (set.numElements() > 0)) {
        state = STATE_WAITING;
        informClient(getNextId(), getNextUID());
      } else if (state != STATE_WAITING) {
        state = STATE_NOTHING;
      }
    }
    
    /**
      * Returns the next unique id for a message
     *
     * @return The next available UID
     */
    protected synchronized int getNextUID() {
      return nextUID++;
    }
    
    /**
      * Interal method which safely takes the next id to be fetched
     * from the set of pending keys
     *
     * @return The next key to be fetched
     */
    protected synchronized Id getNextId() {
      Iterator i = set.getIterator();
      
      if (! i.hasNext()) {
        log.warning(endpoint.getId() + ": GetNextId called without any ids available - aborting");
        return null;
      }
      
      Id result = (Id) set.getIterator().next();  
      set.removeId(result);
      
      log.finer(endpoint.getId() + ": Returing next id to fetch " + result);
      
      return result;
    }
    
    public synchronized void wakeup() {
      if (state == STATE_SLEEPING) {
        send();
      }
    }
    
    public synchronized void message(int id) {
      if ((state == STATE_WAITING) && (id == nextUID - 1)) {
        state = STATE_SLEEPING;
        scheduleNext(); 
      }
    }
  }
  
}








