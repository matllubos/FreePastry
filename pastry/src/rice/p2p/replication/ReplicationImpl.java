
package rice.p2p.replication;

import java.util.*;
import java.util.logging.*;

import rice.*;
import rice.Continuation.*;
import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.environment.params.Parameters;
import rice.p2p.commonapi.*;
import rice.p2p.replication.ReplicationPolicy.*;
import rice.p2p.replication.messaging.*;
import rice.p2p.util.*;
import rice.pastry.leafset.LSRangeCannotBeDeterminedException;

/**
 * @(#) ReplicationImpl.java
 *
 * This is the implementation of the replication manager.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */
public class ReplicationImpl implements Replication, Application {
  /**
   * The amount of time to wait between replications
   */
  public final int MAINTENANCE_INTERVAL;
  
  /**
   * The maximum number of keys to return in one message
   */
  public final int MAX_KEYS_IN_MESSAGE;
  
  /**
   * this application's endpoint
   */
  protected Endpoint endpoint;
  
  /**
   * the local node handle
   */
  protected NodeHandle handle;
  
  /**
   * The factory for create IdSets and IdRanges
   */
  protected IdFactory factory;
  
  /**
   * This replication's client
   */
  protected ReplicationClient client;
  
  /**
   * This replication's policy, which allows for application-specific replication
   */
  protected ReplicationPolicy policy;
  
  /**
   * The replication factor for this replication
   */
  protected int replicationFactor;
  
  /**
   * The instance name of the replication
   */
  protected String instance;
  
  Environment environment;
  
  Logger logger;
  
  /**
   * Constructor
   *
   * @param node The node below this Replication implementation
   * @param client The client for this Replication
   * @param replicationFactor The replication factor for this instance
   * @param instance The unique instance name of this Replication
   */
  public ReplicationImpl(Node node, ReplicationClient client, int replicationFactor, String instance) {
    this(node, client, replicationFactor, instance, new DefaultReplicationPolicy());
  }
  
  /**
   * Constructor
   *
   * @param node The node below this Replication implementation
   * @param client The client for this Replication
   * @param replicationFactor The replication factor for this instance
   * @param instance The unique instance name of this Replication
   */
  public ReplicationImpl(Node node, ReplicationClient client, int replicationFactor, String instance, ReplicationPolicy policy) {
    this.environment = node.getEnvironment();
    logger = environment.getLogManager().getLogger(ReplicationImpl.class, instance);
    
    Parameters p = environment.getParameters();

    MAINTENANCE_INTERVAL = p.getInt("p2p_replication_maintenance_interval");
    MAX_KEYS_IN_MESSAGE = p.getInt("p2p_replication_max_keys_in_message");

    
    this.client = client;
    this.replicationFactor = replicationFactor;
    this.factory = node.getIdFactory();
    this.policy = policy;
    this.instance = instance;
    this.endpoint = node.registerApplication(this, instance);
    
    if (this.policy == null)
      this.policy = new DefaultReplicationPolicy();
    
    this.handle = endpoint.getLocalNodeHandle();
    
  //  log.addHandler(new ConsoleHandler());
  //  log.setLevel(Level.FINER);
  //  log.getHandlers()[0].setLevel(Level.FINER);
    
    if (logger.level <= Logger.FINER) logger.log("Starting up ReplicationImpl with client " + client + " and factor " + replicationFactor);
    
    // inject the first reminder message, which will cause the replication to begin
    // and the next maintenance message to be scheduled
    endpoint.scheduleMessage(new ReminderMessage(handle), environment.getRandomSource().nextInt(MAINTENANCE_INTERVAL), MAINTENANCE_INTERVAL);
  }
  
  /**
   * Internal method which takes returns set A + set B, or all of the members
   * of set A and set B.
   *
   * @param a The first set
   * @param b The second set
   * @return The merge, a+b
   */
  public static IdSet merge(IdFactory factory, IdSet a, IdSet b) {
    IdSet result = factory.buildIdSet();
    Iterator i = a.getIterator();
    
    while (i.hasNext()) {
      result.addId((Id) i.next());
    }
    
    i = b.getIterator();
    
    while (i.hasNext()) {
      result.addId((Id) i.next());
    }
    
    return result;
  }
  
  /**
   * Returns the range for which the local node is an i root, where i can range between
   * 0 and replicationFactor
   * 
   * can return null if the range can't be determined
   *
   * @return The *total* range
   */
  protected IdRange getTotalRange() {
    try {
      return endpoint.range(handle, replicationFactor, handle.getId(), true);
    } catch (RangeCannotBeDeterminedException rcbde) {
      if (logger.level <= Logger.WARNING) logger.log("ReplicationImpl.getTotalRange():"+rcbde+" returning null.");
      return null; 
    }
  }
    
  /**
   * Internal method which updates the client about what his current range is
   */
  private void updateClient() {
    if (logger.level <= Logger.FINE) logger.log( "Updating client with range " + getTotalRange());
    
    if (getTotalRange() != null)
      client.setRange(getTotalRange());
  }
  
  /**
   * This internal method sends out the request messages to all of the nodes
   * which hold keys this node may be interested in
   */
  public void replicate() {
    final NodeHandleSet handles = endpoint.neighborSet(Integer.MAX_VALUE);
    final IdRange ourRange = endpoint.range(handle, 0, handle.getId());  
    
    endpoint.process(new BloomFilterExecutable(ourRange), new ListenerContinuation("Creation of our bloom filter", environment) {
      int total = 0;

      public void receiveResult(Object o) {
        final IdBloomFilter ourFilter = (IdBloomFilter) o;

        for (int i=0; i<handles.size(); i++) {
          final NodeHandle handle = handles.getHandle(i);
          try {
	          final IdRange handleRange = endpoint.range(handle, 0, handle.getId());
	          final IdRange range = handleRange.intersectRange(getTotalRange());
	
	          if ((range != null) && (! range.intersectRange(getTotalRange()).isEmpty())) {
	            endpoint.process(new BloomFilterExecutable(range), new StandardContinuation(this) {
	              public void receiveResult(Object o) {
	                IdBloomFilter filter = (IdBloomFilter) o;
	
	                if (logger.level <= Logger.FINE) logger.log( "COUNT: Sending request to " + handle + " for range " + range + ", " + ourRange + " in instance " + instance);
	                
	                RequestMessage request = new RequestMessage(ReplicationImpl.this.handle, new IdRange[] {range, ourRange}, new IdBloomFilter[] {filter, ourFilter});
	                endpoint.route(null, request, handle);
	              }
	            });
            }
          } catch (LSRangeCannotBeDeterminedException re) {
            // not an error 99.99% of the time, since we're probably just at one end of the range
          }
        }
        
        if (logger.level <= Logger.FINE) logger.log( "COUNT: Done sending replications requests with " + total + " in instance " + instance);
      }
    });
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
    if (logger.level <= Logger.FINE) logger.log( "COUNT: Replication " + instance + " received message " + message);
    
    if (message instanceof RequestMessage) {
      final RequestMessage rm = (RequestMessage) message;
      
      MultiContinuation continuation = new MultiContinuation(new ListenerContinuation("Processing of RequestMessage", environment) {
        public void receiveResult(Object o) {
          Object[] array = (Object[]) o;
          IdSet[] result = new IdSet[array.length];
          System.arraycopy(array, 0, result, 0, array.length);
          
          if (logger.level <= Logger.FINE) logger.log( "COUNT: Telling node " + rm.getSource() + " to fetch");
          endpoint.route(null, new ResponseMessage(handle, rm.getRanges(), result), rm.getSource());
        }
      }, rm.getRanges().length);
      
      for (int i=0; i<rm.getRanges().length; i++) {
        final int j = i;
        endpoint.process(new Executable() {
          public String toString() { return "process " + j + " of " + rm.getRanges().length + " namespace " + instance; }
          public Object execute() {
            IdSet set = factory.buildIdSet();
            rm.getFilters()[j].check(client.scan(rm.getRanges()[j]), set, MAX_KEYS_IN_MESSAGE);

            return set;
          }
        }, continuation.getSubContinuation(i));
      }
    } else if (message instanceof ResponseMessage) {
      ResponseMessage rm = (ResponseMessage) message;
      
      for (int i=0; i<rm.getIdSets().length; i++) {
        IdSet fetch = policy.difference(client.scan(rm.getRanges()[i]), rm.getIdSets()[i], factory);
        
        if (logger.level <= Logger.FINE) logger.log( "COUNT: Was told to fetch " + fetch.numElements() + " in instance " + instance);

        if (fetch.numElements() > 0) 
          client.fetch(fetch, rm.getSource());
      }
    } else if (message instanceof ReminderMessage) {
      replicate(); 
      updateClient(); 
    } else {
      if (logger.level <= Logger.WARNING) logger.log( "Received unknown message " + message + " - dropping on floor.");
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
    updateClient();
  }

  /**
   * Internal class which is an executable for creating a bloom filter
   */
  protected class BloomFilterExecutable implements Executable {
    protected IdRange range;
    
    public BloomFilterExecutable(IdRange range) {
      this.range = range;
    }
    
    public String toString() { 
      return "bloomfilter range " + range + " namespace " + instance; 
    }
    
    public Object execute() {
      return new IdBloomFilter(client.scan(range));
    }
  }  
}








