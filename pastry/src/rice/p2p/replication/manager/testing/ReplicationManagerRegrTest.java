package rice.p2p.replication.manager.testing;

import java.io.IOException;

import rice.Continuation;
import rice.environment.Environment;
import rice.environment.params.Parameters;
import rice.environment.params.simple.SimpleParameters;
import rice.p2p.commonapi.*;
import rice.p2p.commonapi.testing.CommonAPITest;
import rice.p2p.replication.manager.*;
import rice.pastry.direct.DirectTimeSource;

/**
 * @(#) ReplicationRegrTest.java Provides regression testing for the replication manager service using distributed
 * nodes.
 *
 * @version $Id$
 * @author Alan Mislove
 */
public class ReplicationManagerRegrTest extends CommonAPITest {

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
  protected ReplicationManagerImpl[] replications;

  /**
   * The clients
   */
  protected TestReplicationManagerClient[] clients;

  /**
   * Constructor which sets up all local variables
   */
  public ReplicationManagerRegrTest(Environment env) throws IOException {
    super(env);
    replications = new ReplicationManagerImpl[NUM_NODES];
    clients = new TestReplicationManagerClient[NUM_NODES];
  }


  /**
   * Usage: ReplicationRegrTest [-port p] [-bootstrap host[:port]] [-nodes n] [-protocol (rmi|wire)]
   * [-help]
   *
   * @param args DESCRIBE THE PARAMETER
   */
  public static void main(String args[]) throws IOException {
    parseArgs(args);
    Parameters param = new SimpleParameters(Environment.defaultParamFileArray,null);
    param.setString("loglevel","ALL");
    param.setBoolean("environment_logToFile",true);
    param.setString("fileLogManager_filePrefix","retest_");
    param.setString("fileLogManager_fileSuffix",".log");
    Environment env = new Environment(null,null,null,
//        null,
        new DirectTimeSource(System.currentTimeMillis()),
        null,
        param);
    ReplicationManagerRegrTest test = new ReplicationManagerRegrTest(env);
    test.start();
  }

  /**
   * Method which should process the given newly-created node
   *
   * @param node The newly created node
   * @param num The number of this node
   */
  protected void processNode(int num, Node node) {
    clients[num] = new TestReplicationManagerClient(node);
    replications[num] = new ReplicationManagerImpl(node, clients[num], REPLICATION_FACTOR, INSTANCE);
  }

  /**
   * Method which should run the test - this is called once all of the nodes have been created and
   * are ready.
   */
  protected void runTest() {
    for (int i=0; i<NUM_NODES; i++)
      simulate(); 
    
    testBasic();
    testOverload();
    testStress();
    testMaintenance();
  }

  /*
   *  ---------- Test methods and classes ----------
   */
  
  /**
    * Tests basic functionality
   */
  public void testBasic() {
    int num = environment.getRandomSource().nextInt(NUM_NODES);
    Id id = nodes[num].getId();
    
    IdRange all = FACTORY.buildIdRange(FACTORY.buildId(new byte[20]), FACTORY.buildId(new byte[20]));
    
    sectionStart("Testing Basic Functionality");
    
    stepStart("Inserting Object");
    
    clients[num].insert(id);
    
    stepDone(SUCCESS);
    
    stepStart("Initiating Maintenance");
    
    runMaintenance();
    
    for (int i=0; i<NUM_NODES; i++)
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
    int num = environment.getRandomSource().nextInt(NUM_NODES);
    Id id = nodes[num].getId();
    
    IdRange all = FACTORY.buildIdRange(FACTORY.buildId(new byte[20]), FACTORY.buildId(new byte[20]));
    
    sectionStart("Testing Maintenance Functionality");
    
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
  
  /**
    * Tests basic functionality
   */
  public void testOverload() {
    int NUM_TO_INSERT = 16;
    int num = environment.getRandomSource().nextInt(NUM_NODES);
    Id id = nodes[num].getId();
    
    IdRange all = FACTORY.buildIdRange(FACTORY.buildId(new byte[20]), FACTORY.buildId(new byte[20]));
    
    sectionStart("Testing Overload Functionality");
    
    stepStart("Inserting " + NUM_TO_INSERT + " Objects");
    
    for (int i=0; i<NUM_TO_INSERT; i++) {
      clients[num].insert(addToId(id, i));
      simulate();
    }
      
    stepDone(SUCCESS);
    
    stepStart("Initiating Maintenance");
    
    runMaintenance();
    simulate();
  
    for (int i=0; i<NUM_TO_INSERT+1; i++) {
      try {
        Thread.sleep(replications[0].FETCH_DELAY);
      } catch (InterruptedException e) {
        System.out.println(e.toString());
      }
      
      simulate();
    }
      
    for (int j=0; j<NUM_TO_INSERT; j++) {
      int count = 0;
      
      Id thisId = addToId(id, j);
      
      for (int i=0; i<NUM_NODES; i++)  {
        if (clients[i].scan(all).isMemberId(thisId)) 
          count++;
      }
      
      assertTrue("Correct number of replicas for " + thisId + " should be " + (REPLICATION_FACTOR + 1) + " was " + count, 
                 count == REPLICATION_FACTOR + 1);
    }
    
    stepDone(SUCCESS);
    
    sectionDone();
  }
  
  /**
    * Tests basic functionality
   */
  public void testStress() {
    int NUM_TO_INSERT = 45;
    Id[] ids = new Id[NUM_TO_INSERT];
    int num = environment.getRandomSource().nextInt(NUM_NODES);
    Id id = nodes[num].getId();
    
    IdRange all = FACTORY.buildIdRange(FACTORY.buildId(new byte[20]), FACTORY.buildId(new byte[20]));
    
    sectionStart("Testing Stressed Functionality");
    
    stepStart("Inserting " + NUM_TO_INSERT + " Objects");
    
    for (int i=0; i<NUM_TO_INSERT; i++) {
      ids[i] = addToId(id, i);
      clients[num].insert(ids[i]);
    }
    
    stepDone(SUCCESS);
    
    stepStart("Initiating Maintenance");
    
    runMaintenance();
    simulate();
    
    try {
      Thread.sleep(25000);
    } catch (InterruptedException e) {
      System.out.println(e.toString());
    }
    
    simulate();
    
    for (int j=0; j<NUM_TO_INSERT; j++) {
      int count = 0;
      
      Id thisId = ids[j];
      
      for (int i=0; i<NUM_NODES; i++)  {
        if (clients[i].scan(all).isMemberId(thisId)) 
          count++;
      }
      
      assertTrue("Correct number of replicas for " + j + " " + thisId + " should be " + (REPLICATION_FACTOR + 1) + " was " + count, 
                 count == REPLICATION_FACTOR + 1);
    }
    
    stepDone(SUCCESS);
    
    sectionDone();
  }
  
  public void runMaintenance() {
    for (int i=0; i<NUM_NODES; i++) {
      replications[i].getReplication().replicate();
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
    environment.getRandomSource().nextBytes(data);
    return FACTORY.buildId(data);
  }
  
  private Id addToId(Id id, int num) {
    byte[] bytes = id.toByteArray();
    bytes[0] += num;
    
    return FACTORY.buildId(bytes);
  }

  /**
   * DESCRIBE THE CLASS
   *
   * @version $Id$
   * @author amislove
   */
  protected class TestReplicationManagerClient implements ReplicationManagerClient {
        
    public Node node;
    
    public IdSet set;
    
    public TestReplicationManagerClient(Node node) {
      this.set = node.getIdFactory().buildIdSet();
      this.node = node;
    }
    
    public void fetch(Id id, NodeHandle hint, Continuation command) {
      set.addId(id);
      command.receiveResult(new Boolean(true));
    }
    
    public void remove(Id id, Continuation command) {
      set.removeId(id);
      command.receiveResult(new Boolean(true));
    }
    
    public IdSet scan(IdRange range) {
      return set.subSet(range);
    }
    
    public void insert(Id id) {
      set.addId(id);
    }
    
    public boolean exists(Id id) {
      return set.isMemberId(id);
    }

    public void existsInOverlay(Id id, Continuation command) {
      // XXX we don't test this new functionality yet
      command.receiveResult(Boolean.TRUE);
    }

    public void reInsert(Id id, Continuation command) {
      // XXX we don't test this new functionality yet
      command.receiveResult(Boolean.TRUE);
    }
  }
}














