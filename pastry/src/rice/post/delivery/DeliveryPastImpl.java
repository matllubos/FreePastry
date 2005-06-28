
package rice.post.delivery;

import java.util.*;

import rice.*;
import rice.Continuation.*;
import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.post.*;
import rice.post.messaging.*;
import rice.p2p.commonapi.*;
import rice.p2p.past.*;
import rice.p2p.past.gc.*;
import rice.persistence.*;

/**
 * @(#) DeliveryyPastImpl.java
 * 
 * This class extends normal Past functionality in order to support 
 * the Post-specific storage for pending delivery messages.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */
public class DeliveryPastImpl extends GCPastImpl implements DeliveryPast {

  protected int redundancy;
  
  protected PastImpl delivered;
  
  protected IdFactory factory;
  
  /**
   * Constructor for DeliveryPastImpl
   *
   * @param node The node below this Past implementation
   * @param manager The storage manager to be used by Past
   * @param replicas The number of object replicas
   * @param instance The unique instance name of this Past
   */
  public DeliveryPastImpl(Node node, StorageManager manager, Cache backup, int replicas, int redundancy, String instance, PastImpl delivered, long collectionInterval, Environment env) {
    super(node, manager, backup, replicas, instance + "-delivery", new PastPolicy.DefaultPastPolicy(), collectionInterval, null, env);
    
    this.redundancy = redundancy;
    this.delivered = delivered;
    this.factory = node.getIdFactory();
  }
  
  /**
   * This upcall is invoked to tell the client to fetch the given id, 
   * and to call the given command with the boolean result once the fetch
   * is completed.  The client *MUST* call the command at some point in the
   * future, as the manager waits for the command to return before continuing.
   *
   * @param id The id to fetch
   */
  public void fetch(Id id, NodeHandle hint, Continuation command) {
    log(Logger.FINER, endpoint.getId() + ": Told to fetch Id " + id);
    
    if (delivered.exists(((GCId) id).getId())) {
      log(Logger.FINER, endpoint.getId() + ": Skipping Id " + id + " because we have receipt.");
      command.receiveResult(new Boolean(true));
    } else {
      log(Logger.FINER, endpoint.getId() + ": Actually fetching Id " + id);
      super.fetch(id, hint, command);
    }
  }
  
  /**
   * Method which periodically checks to see if we've got receipts for
   * any outstanding messages.  If so, then we remove the outstanding message
   * from our pending list.
   */
  public void synchronize(Continuation command) { 
    log(Logger.FINER, endpoint.getId() + ": Synchronizing range " + endpoint.range(endpoint.getLocalNodeHandle(), getReplicationFactor()+1, null, true));
    
    GCIdRange range = (GCIdRange) endpoint.range(endpoint.getLocalNodeHandle(), getReplicationFactor()+1, null, true);
    
    if (range == null)
      return;
    
    final Iterator i = storage.getStorage().scan(range.getRange()).getIterator();

    Continuation c = new StandardContinuation(command) {      
      public void receiveResult(Object o) {
        if (! Boolean.TRUE.equals(o))
          log(Logger.WARNING, endpoint.getId() + ": Removal of delivered message caused " + o);
        
        while (i.hasNext()) {
          final Id id = (Id) i.next();

          if (delivered.exists(id)) {
            final Continuation me = this;
            
            log(Logger.FINER, endpoint.getId() + ": Deleting id " + id + " because receipt exists");
            storage.unstore(id, new StandardContinuation(parent) {
              public void receiveResult(Object o) {
                if (! Boolean.TRUE.equals(o))
                  log(Logger.WARNING, endpoint.getId() + ": Removal of delivered message caused " + o);

                if (backup == null) 
                  me.receiveResult(o);
                else 
                  backup.uncache(id, me);
              }
            });
            
            return;
          }
        } 
        
        log(Logger.FINER, endpoint.getId() + ": Done Synchronizing...");
        parent.receiveResult(new Boolean(true));
      }
    };
    
    c.receiveResult(new Boolean(true));
  }
  
  /**
   * Returns the list of PostEntityaddress for which we are the primary replica
   * responsible for delivering messages.
   *
   * @param command The command to return the results to
   */
  public void getGroups(final Continuation command) {    
    log(Logger.FINER, "Getting list of groups...");
    GCIdRange range = (GCIdRange) endpoint.range(endpoint.getLocalNodeHandle(), redundancy, null, true);

    final Iterator i = storage.getStorage().scan(range.getRange()).getIterator();
    
    Continuation c = new StandardContinuation(command) {
      HashSet result = new HashSet();
      
      public void receiveResult(Object o) {
        while (i.hasNext()) {
          Id id = (Id) i.next();
          GCPastMetadata metadata = (GCPastMetadata) storage.getMetadata(id);

          if ((metadata != null) && (metadata instanceof DeliveryMetadata)) {
            result.add(((DeliveryMetadata) metadata).getDestination());
          } else {
            setMetadata(id, this);
            return;
          }
        } 
        
        log(Logger.FINER, "Return list of " + result.size() + " groups");
        parent.receiveResult(result.toArray(new PostEntityAddress[0]));
      }
    };
    
    c.receiveResult(null);
  }
  
  /**
    * Returns the first message which is still pending to the given address.  If no
   * such message exists, null is returned
   *
   * @param address The address for the message
   * @param command The command to return the results to
   */
  public void getMessage(final PostEntityAddress address, final Continuation command) {
    GCIdRange range = (GCIdRange) endpoint.range(endpoint.getLocalNodeHandle(), redundancy, null, true);
    Iterator i = storage.getStorage().scan(range.getRange()).getIterator();
    
    while (i.hasNext()) {
      Id id = (Id) i.next();
      GCPastMetadata metadata = (GCPastMetadata) storage.getMetadata(id);
        
      if ((metadata != null) && (metadata instanceof DeliveryMetadata) && 
          ((DeliveryMetadata) metadata).getDestination().equals(address)) {
        storage.getObject(id, command);
        return;
      }
    }      
     
    environment.getLogManager().getLogger(DeliveryPastImpl.class, instance).log(Logger.WARNING,
        "Could not find any messages for user " + address + " - not tragic, but strange...");
      
    command.receiveResult(null);
  }
  
  /**
   * Either returns the userid associated with the given id by looking in the cache,
   * or reads it off of disk.
   *
   * @param id The id to return the PostEntityAddress of
   * @param command The command to return the result to
   */
  protected void setMetadata(final Id id, Continuation command) {
    final GCPastMetadata metadata = (GCPastMetadata) storage.getMetadata(id);
    
    storage.getObject(id, new StandardContinuation(command) {
      public void receiveResult(Object o) {
        storage.setMetadata(id, ((Delivery) o).getMetadata((metadata == null ? DEFAULT_EXPIRATION : metadata.getExpiration())), parent);
      }
    });
  }
}

