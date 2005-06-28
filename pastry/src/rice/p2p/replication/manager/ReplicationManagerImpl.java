
package rice.p2p.replication.manager;

import java.util.*;
import java.util.logging.*;

import rice.*;
import rice.Continuation.*;

import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.environment.params.Parameters;
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
  public final int FETCH_DELAY;
  
  /**
   * The amount of time to wait before giving up on a client fetch
   */
  public final int TIMEOUT_DELAY;
  
  /**
   * The number of ids to delete at a given time - others will be deleted later 
   */
  public final int NUM_DELETE_AT_ONCE;
  
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
   * The deleter, for managing ids to delete
   */
  protected ReplicationManagerDeleter deleter;
    
  protected String instance;
  
  protected Environment environment;
  
  /**
    * Constructor
   *
   * @param node The node below this Replication implementation
   * @param client The client for this Replication
   * @param replicationFactor The replication factor for this instance
   * @param instance The unique instance name of this Replication
   */
  public ReplicationManagerImpl(Node node, ReplicationManagerClient client, int replicationFactor, String instance, Environment env) {
    this(node, client, replicationFactor, instance, null, env);
  }
  
  /**
   * Constructor
   *
   * @param node The node below this Replication implementation
   * @param client The client for this Replication
   * @param replicationFactor The replication factor for this instance
   * @param instance The unique instance name of this Replication
   * @param policy The replication policy to use
   */
  public ReplicationManagerImpl(Node node, ReplicationManagerClient client, int replicationFactor, String instance, ReplicationPolicy policy, Environment env) {
    this.environment = env;
    Parameters p = environment.getParameters();
    
    FETCH_DELAY = p.getInt("p2p_replication_manager_fetch_delay");
    TIMEOUT_DELAY = p.getInt("p2p_replication_manager_timeout_delay");
    NUM_DELETE_AT_ONCE = p.getInt("p2p_replication_manager_num_delete_at_once");
    
    this.client = client;
    this.factory = node.getIdFactory();
    this.endpoint = node.registerApplication(this, instance);
    this.helper = new ReplicationManagerHelper();
    this.deleter = new ReplicationManagerDeleter();
    this.instance = instance;
    
    log(Logger.FINE, "Starting up ReplicationManagerImpl with client " + client);
    
    this.replication = new ReplicationImpl(node, this, replicationFactor, instance, policy, env);
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
   * @param hint The hint where the id may be
   */
  protected void informClient(final Id id, NodeHandle hint) {
    log(Logger.FINE, "Telling client to fetch id " + id);
  
    final CancellableTask timer = endpoint.scheduleMessage(new TimeoutMessage(id), TIMEOUT_DELAY);
    
    client.fetch(id, hint, new Continuation() {
      public void receiveResult(Object o) {
        if (! (new Boolean(true)).equals(o)) {
          log(Logger.WARNING, "Fetching of id " + id + " failed with " + o);
        }
        
        log(Logger.FINE, "Successfully fetched id " + id);
        
        timer.cancel();
        helper.message(id);
      }
      
      public void receiveException(Exception e) {
        receiveResult(e);
      }
    });
  }
  
  /**
   * Internal method which schedules the next reminder message (if it is necessary),
   * or simply resets the active flag if there's nothing to be fetched.
   */
  protected void scheduleNext() {
    log(Logger.FINER, "Scheduling next fetch in " + FETCH_DELAY + " milliseconds");
    
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
  public void fetch(IdSet keySet, NodeHandle hint) {
   // log.finer(endpoint.getId() + ": Adding keyset " + keySet + " to the list of pending ids");
    helper.fetch(keySet, hint);
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
  public void setRange(final IdRange range) {
    log(Logger.FINEST, "Removing range " + range + " from the list of pending ids");

    helper.setRange(range);
    deleter.setRange(range);
  }    
  
  /**
   * This upcall should return the set of keys that the application
   * currently stores in this range. Should return a empty IdSet (not null),
   * in the case that no keys belong to this range.
   *
   * In this case, it returns the list of keys the client has, along with the
   * keys which we have yet to tell the client to fetch.
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
      log(Logger.FINEST, "Received reminder message");
      helper.wakeup();
    } else if (message instanceof TimeoutMessage) {
      log(Logger.FINEST, "Received timeout message");
      helper.message(((TimeoutMessage) message).getId());
    } else {
      log(Logger.WARNING, "Received unknown message " + message);
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
  public Replication getReplication() {
    return replication;
  }
  
  private void log(int level, String str) {
    environment.getLogManager().getLogger(ReplicationImpl.class, instance).log(level, str); 
  }  
  
  private void logException(int level, String str, Exception e) {
    environment.getLogManager().getLogger(ReplicationImpl.class, instance).logException(level,str, e); 
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
    protected Id current;
    
    /**
     * A cache of hints, mapping Id -> NodeHandle
     */
    protected HashMap hints;
    
    /**
     * Constructor 
     */
    public ReplicationManagerHelper() {
      set = factory.buildIdSet();
      hints = new HashMap();
      state = STATE_NOTHING;
    }
    
    /**
     * Method by which keys are added to the list of keys to fetch
     *
     * @param keySet The keys to add
     */
    public synchronized void fetch(IdSet keySet, NodeHandle hint) {
      Iterator i = keySet.getIterator();

      while (i.hasNext()) {
        Id id = (Id) i.next();
        
        if (! (set.isMemberId(id) || 
               client.exists(id) || 
               ((current != null) && (id.equals(current))))) {
          set.addId(id);
          hints.put(id, hint);
        }
      }
        
      if ((state == STATE_NOTHING) && (set.numElements() > 0)) {
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
      Iterator i = set.subSet(notRange).getIterator();
      
      /* now look for any matching ids */
      while (i.hasNext()) {
        Id id = (Id) i.next();
        set.removeId(id);
        hints.remove(id);
      }
    }    
    
    /**
     * In this case, it returns the list of keys the client has, along with the
     * keys which we have yet to tell the client to fetch.
     *
     * @param range the requested range
     */
    public IdSet scan(IdRange range) {
      return set.subSet(range);
    }
    
    /**
     * Method which determines if a message should be sent, and if so, sends it
     */
    protected synchronized void send() {
      if ((state != STATE_WAITING) && (set.numElements() > 0)) {
        Id id = getNextId();
        NodeHandle hint = (NodeHandle) hints.remove(id);
        
        if (id != null) {
          state = STATE_WAITING;
          informClient(id, hint);
        } else {
          state = STATE_NOTHING;
        }
      } else if (state != STATE_WAITING) {
        state = STATE_NOTHING;
      }
    }
    
    /**
     * Interal method which safely takes the next id to be fetched
     * from the set of pending keys
     *
     * @return The next key to be fetched
     */
    protected synchronized Id getNextId() {      
      if (set.numElements() == 0) {
        log(Logger.WARNING, "GetNextId called without any ids available - aborting");
        return null;
      }
      
      current = (Id) set.getIterator().next();  
      set.removeId(current);
      
      log(Logger.FINER, "Returing next id to fetch " + current);
      
      if (! client.exists(current))
        return current;
      else
        return getNextId();
    }
    
    public synchronized void wakeup() {
      if (state == STATE_SLEEPING) {
        send();
      }
    }
    
    public synchronized void message(Id id) {
      if ((state == STATE_WAITING) && (current != null) && (current.equals(id))) {
        state = STATE_SLEEPING;
        current = null;
        scheduleNext(); 
      }
    }
  }
  
  /**
   * Inner class which keeps track of the keys which we are currently deleting
   */
  protected class ReplicationManagerDeleter implements Continuation {
    
    /**
     * The set of ids we are responsible for deleting
     */
    protected IdSet set;
    
    /**
     * Whether or not we are waiting for a response
     */
    protected Id id;
    
    /**
     * Bulds a new one
     */
    public ReplicationManagerDeleter() {
      set = factory.buildIdSet();
    }
    
    /**
     * Adds a set of ids to the to-delete list
     *
     * @param range The current responsible range
     */
    public synchronized void setRange(IdRange range) {
      IdRange notRange = range.getComplementRange();    

      // first, we add all of the clients stuff in the not-range 
      Iterator i = client.scan(notRange).getIterator();
      int count = 0;
      
      while (i.hasNext() && (count < NUM_DELETE_AT_ONCE)) {
        count++;
        Id next = (Id) i.next();
        
        if ((id == null) || (! (id.equals(next))))
          set.addId(next);
      }
      
      // next, we remove and ids from the to-delete list which are not in the range
      Iterator j = set.subSet(range).getIterator();
      
      while (j.hasNext()) 
        set.removeId((Id) j.next());
      
      go();
    }
     
    /**
     * Internal method which starts the deleting, if it's not already started
     */
    protected synchronized void go() {
      if ((id == null) && (set.numElements() > 0)) {
        id = (Id) set.getIterator().next();
        set.removeId(id);
        
        log(Logger.FINER, "Telling client to delete id " + id);
        log(Logger.FINER, "RMImpl.go " + instance + ": removing id " + id);
        
        client.remove(id, this);
      }
    }
    
    /**
     * Implementation of continuation
     *
     * @param o The result
     */
    public synchronized void receiveResult(Object o) {
      if (id == null) 
        log(Logger.SEVERE, "ERROR: RMImpl.deleter Received result " + o + " unexpectedly!");
      
      if (! Boolean.TRUE.equals(o)) 
        log(Logger.SEVERE, "ERROR: RMImpl.deleter Unstore of " + id + " did not succeed '" + o + "'!");
      
      id = null;
      go();
    }
    
    /**
     * Implementation of continuation
     *
     * @param o The result
     */
    public synchronized void receiveException(Exception e) {
      logException(Logger.SEVERE, "RMImpl.deleter Unstore of " + id + " caused exception '" + e + "'!", e);
      
      id = null;
      go();
    }
  }
}








