package rice.p2p.replication.testing;

import java.io.Serializable;
import java.net.*;

import java.util.*;

import rice.*;
import rice.Continuation.*;

import rice.persistence.*;

import rice.p2p.commonapi.*;
import rice.p2p.commonapi.testing.*;
import rice.p2p.replication.*;

/**
 * @(#) ReplicationRegrTest.java Provides regression testing for the replication service using distributed
 * nodes.
 *
 * @version $Id$
 * @author Alan Mislove
 */
public class ReplicationRegrTest extends CommonAPITest {

  /**
   * The replication factor to use
   */
  public static int REPLICATION_FACTOR = 3;
  
  /**
   * the instance name to use
   */
  public static String INSTANCE = "ReplicationRegrTest";

  /**
   * the replication impls in the ring
   */
  protected ReplicationImpl[] replications;

  /**
   * The clients
   */
  protected TestReplicationClient[] clients;

  /**
   * random number generator
   */
  protected Random rng;

  /**
   * Constructor which sets up all local variables
   */
  public ReplicationRegrTest() {
    replications = new ReplicationImpl[NUM_NODES];
    clients = new TestReplicationClient[NUM_NODES];
    rng = new Random();
  }


  /**
   * Usage: ReplicationRegrTest [-port p] [-bootstrap host[:port]] [-nodes n] [-protocol (rmi|wire)]
   * [-help]
   *
   * @param args DESCRIBE THE PARAMETER
   */
  public static void main(String args[]) {
    parseArgs(args);
    ReplicationRegrTest test = new ReplicationRegrTest();
    test.start();
  }

  /**
   * Method which should process the given newly-created node
   *
   * @param node The newly created node
   * @param num The number of this node
   */
  protected void processNode(int num, Node node) {
    clients[num] = new TestReplicationClient(node);
    replications[num] = new ReplicationImpl(node, clients[num], REPLICATION_FACTOR, INSTANCE);
  }

  /**
   * Method which should run the test - this is called once all of the nodes have been created and
   * are ready.
   */
  protected void runTest() {
    testBasic();
    testMaintenance();
  }

  /*
   *  ---------- Test methods and classes ----------
   */
  
  /**
    * Tests basic functionality
   */
  public void testBasic() {
    int num = rng.nextInt(NUM_NODES);
    Id id = nodes[num].getId();
    
    IdRange all = FACTORY.buildIdRange(FACTORY.buildId(new byte[20]), FACTORY.buildId(new byte[20]));
    
    sectionStart("Testing Basic Functionality");
    
    stepStart("Inserting Object");
    
    clients[num].insert(id);
    
    stepDone(SUCCESS);
    
    stepStart("Initiating Maintenance");
    
    runMaintenance();
    simulate();
    
    int count = 0;
    
    for (int i=0; i<NUM_NODES; i++)  {
      if (clients[i].scan(all).isMemberId(id)) 
        count++;
    }
  
    assertTrue("Correct number of replicas should be " + (REPLICATION_FACTOR + 1) + " was " + count, 
               count == REPLICATION_FACTOR + 1);
    
    stepDone(SUCCESS);
    
    sectionDone();
  }
  
  /**
    * Tests maintenance functionality
   */
  public void testMaintenance() {
    int num = rng.nextInt(NUM_NODES);
    Id id = nodes[num].getId();
    
    IdRange all = FACTORY.buildIdRange(FACTORY.buildId(new byte[20]), FACTORY.buildId(new byte[20]));
    
    sectionStart("Testing Basic Functionality");
    
    stepStart("Inserting Object");
    
    clients[num].insert(id);
    
    stepDone(SUCCESS);
    
    stepStart("Initiating Maintenance");
    
    runMaintenance();
    simulate();
    
    int count = 0;
    
    for (int i=0; i<NUM_NODES; i++)  {
      if (clients[i].scan(all).isMemberId(id)) 
        count++;
    }
    
    assertTrue("Correct number of replicas should be " + (REPLICATION_FACTOR + 1) + " was " + count, 
               count == REPLICATION_FACTOR + 1);
    
    stepDone(SUCCESS);
    
    stepStart("Killing Primary Replica");
    
    kill(num);
    
    stepDone(SUCCESS);
    
    stepStart("Initiating Maintenance");
    
    runMaintenance();
    simulate();
    
    count = 0;
    
    for (int i=0; i<NUM_NODES; i++)  {
      if (clients[i].scan(all).isMemberId(id)) 
        count++;
    }
    
    assertTrue("Correct number of replicas should be " + (REPLICATION_FACTOR + 2) + " was " + count, 
               count == REPLICATION_FACTOR + 2);
    
    stepDone(SUCCESS);

    sectionDone();
  }
  
  public void runMaintenance() {
    for (int i=0; i<NUM_NODES; i++) {
      replications[i].replicate();
    }
    
    simulate();
  }

  /**
   * Private method which generates a random Id
   *
   * @return A new random Id
   */
  private Id generateId() {
    byte[] data = new byte[20];
    rng.nextBytes(data);
    return FACTORY.buildId(data);
  }

  /**
   * DESCRIBE THE CLASS
   *
   * @version $Id$
   * @author amislove
   */
  protected class TestReplicationClient implements ReplicationClient {
    
    public MemoryStorage storage;
    
    public Node node;
    
    public TestReplicationClient(Node node) {
      this.storage = new MemoryStorage(FACTORY);
      this.node = node;
    }
    
    /**
     * This upcall is invoked to notify the application that is should
     * fetch the cooresponding keys in this set, since the node is now
     * responsible for these keys also.
     *
     * @param keySet set containing the keys that needs to be fetched
     */
    public void fetch(IdSet keySet) {
      Iterator i = keySet.getIterator();
      
      while (i.hasNext()) {
        Id next = (Id) i.next();
        storage.store(next, next, new ListenerContinuation("Insertion of " + next));
      }
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
      IdRange notRange = range.getComplementRange();
      IdSet set = storage.scan(notRange);
      
      Iterator i = set.getIterator();
      
      while (i.hasNext()) {
        Id next = (Id) i.next();
        storage.unstore(next, new ListenerContinuation("Removal of " + next));
      }
    }
    
    /**
     * This upcall should return the set of keys that the application
     * currently stores in this range. Should return a empty IdSet (not null),
     * in the case that no keys belong to this range.
     *
     * @param range the requested range
     */
    public IdSet scan(IdRange range) {
      return storage.scan(range);
    }
    
    public void insert(Id id) {
      storage.store(id, id, new ListenerContinuation("Insertion of id " + id));
    }
  }
}














