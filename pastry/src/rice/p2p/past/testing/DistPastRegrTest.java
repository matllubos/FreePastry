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

package rice.p2p.past.testing;

import rice.*;

import rice.p2p.commonapi.*;
import rice.p2p.commonapi.testing.*;
import rice.p2p.past.*;

import rice.persistence.*;

import java.util.*;
import java.net.*;
import java.io.Serializable;

/**
 * Provides regression testing for the Past service using distributed nodes.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */

public class DistPastRegrTest extends DistCommonAPITest {

  // the instance name to use
  public static String INSTANCE = "DistPastRegrTest";
  
  // the replication factor in Past
  public static int REPLICATION_FACTOR = 3;
  
  // the past impls in the ring
  protected Past pasts[];

  // a random number generator
  protected Random rng;

  /**
   * Constructor which sets up all local variables
   */
  public DistPastRegrTest() {
    pasts = new Past[NUM_NODES];
    rng = new Random();
  }

  /**
   * Method which should process the given newly-created node
   *
   * @param node The newly created node
   */
  protected void processNode(int num, Node node) {
    StorageManager storage = new StorageManager(FACTORY,
                                                new MemoryStorage(FACTORY),
                                                new LRUCache(new MemoryStorage(FACTORY), 100000));
    pasts[num] = new PastImpl(node, storage, REPLICATION_FACTOR, INSTANCE);
  }

  /**
   * Method which should run the test - this is called once all of the
   * nodes have been created and are ready.
   */
  protected void runTest() {
    // Run each test
   // testRouteRequest();
    //testPastFunctions();
   // new TestPastFunctions().start();
  }

  /* ---------- Test methods and classes ---------- */

  /**
   * Tests routing a Past request to a particular node.
   */
  protected void testRouteRequest() throws TestFailedException {
    final PastService local = pasts[rng.nextInt(NUM_NODES)];
    final PastServiceImpl remote = pasts[rng.nextInt(NUM_NODES)];
    final Id remoteId = remote.getId();
    final String file = "test file";

    // Check file does not exist
    local.exists(remoteId, new TestCommand() {
      public void receive(Object result) throws Exception {
        assertTrue("RouteRequest", "File should not exist before insert",
                   !((Boolean)result).booleanValue());
        
        // Insert file
        System.out.println("TEST: RouteRequest: Inserting file with key: " + remoteId);
        local.insert(remoteId, file, new TestCommand() {
          public void receive(Object result) throws Exception {
            assertTrue("RouteRequest", "Insert of file should succeed",
                       ((Boolean)result).booleanValue());
    
            // Check file exists
            local.exists(remoteId, new TestCommand() {
              public void receive(Object result) throws Exception {
                assertTrue("RouteRequest", "File should exist after insert",
                           ((Boolean)result).booleanValue());

                // Lookup file locally
                remote.getStorage().getObject((rice.pastry.Id) remoteId, new TestCommand() {
                  public void receive(Object result) throws Exception {
                    assertTrue("RouteRequest", "File should be inserted at known node",
                               result != null);
                    assertEquals("RouteRequest", "Retrieved local file should be the same",
                                 file, result);

                    // DONE WITH THE TEST!
                    System.out.println("\n\n---- testRouteRequest passed! ---------------------\n");
                  }
                });
              }
            });
          }
        });
      }
    });

  }
  
  /**
   * Commands for running the Past functions test.
   */
  protected class TestPastFunctions {
    final Credentials userCred;
    final PastService local;
    final Id fileId;
    final String file;
    final String update;
    
    int localCount;
    int currentIndex;
    PastServiceImpl remote;
    
    /**
     * Sets up this test.
     */
    public TestPastFunctions() {
      userCred = null;
      local = (PastService) pastNodes.elementAt(rng.nextInt(NUM_NODES));
      fileId = idFactory.generateNodeId();
      file = "test file";
      update = "update to file";
      localCount = 0;
      currentIndex = 0;
      remote = null;
    }
    
    /**
     * Starts running this test.
     */
    public void start() {
      runInsertTests();
    }
    
    /**
     * Checks that a file is not available until it is inserted,
     * and that it can only be inserted once.
     */
    protected void runInsertTests() {
      // Try looking up before insert
      local.lookup(fileId, new TestCommand() {
        public void receive(Object test) throws Exception {
          assertTrue("PastFunctions", "Lookup before insert should fail",
                     test == null);
          
          // Should not exists
          local.exists(fileId, new TestCommand() {
            public void receive(Object exists) throws Exception {
              assertTrue("PastFunctions", "File should not exist before insert",
                         !((Boolean)exists).booleanValue());
              
              // Insert file
              System.out.println("TEST: PastFunctions: Inserting file with key: " + fileId);
              local.insert(fileId, file,  new TestCommand() {
                public void receive(Object success) throws Exception {
                  assertTrue("PastFunctions", "Insert of file should succeed",
                             ((Boolean)success).booleanValue());
                  
                  // Should exist
                  local.exists(fileId, new TestCommand() {
                    public void receive(Object exists) throws Exception {
                      assertTrue("PastFunctions", "File should exist after insert",
                                 ((Boolean)exists).booleanValue());
                      
                      // Try to insert again
                      local.insert(fileId, file,  new TestCommand() {
                        public void receive(Object success) throws Exception {
                          assertTrue("PastFunctions", 
                                     "Re-insert of file should fail",
                                     !((Boolean)success).booleanValue());
                          
                          runInsertChecks();
                        }
                      });
                    }
                  });
                }
              });
            }
          });
        }
      });
    }
    
    /**
     * Checks that a file is stored on the right number of nodes after
     * being inserted.
     */
    protected void runInsertChecks() {
      // "Loop" to perform a check on each node
      //   (Runs the same command several times, incrementing currentIndex field)
                         pause(200000);
      localCount = 0;
      currentIndex = 0;
      remote = (PastServiceImpl) pastNodes.elementAt(currentIndex);
      remote.lookup(fileId, new TestCommand() {
        public void receive(Object res) throws Exception {
          Serializable result = (Serializable) res;
          
          // Check that file is found using Past, but has no updates
          assertTrue("PastFunctions", "File should always be found remotely",
                     result != null);
          assertEquals("PastFunctions", 
                       "Retrieved file should be the same, node " + currentIndex,
                       file, result);

          final TestCommand MONKEY = this;
         
          // Lookup file locally (using Storage)
          remote.getStorage().getObject((rice.pastry.Id) fileId, new TestCommand() {
            public void receive(Object result) throws Exception {
              if (result != null) {
                System.out.println("TEST: Found file locally on node " + currentIndex);
                localCount++;
                assertEquals("PastFunctions",
                             "Retrieved local file should be the same, node " + currentIndex,
                             file, result);
              }

              // Now check if we've visited all nodes
              currentIndex++;
              if (currentIndex < pastNodes.size()) {
                // Perform this check on the next node
                remote = (PastServiceImpl) pastNodes.elementAt(currentIndex);
                remote.lookup(fileId, MONKEY);
              } else {
                assertEquals("PastFunctions",
                             "File should have been found " + remote.getReplicaFactor() + " time after insert",
                             new Integer(remote.getReplicaFactor()), new Integer(localCount));

                runReclaimTests();
              }
            }
          });
        }
      });
    } 

    /**
     * Checks that deleting a file works.
     */
    protected void runReclaimTests() {
      
      // Reclaim space used by file
      System.out.println("TEST: Reclaiming file with key: " + fileId);
      local.delete(fileId, new TestCommand() {
        public void receive(Object success) throws Exception {
          assertTrue("PastFunctions", "File should be reclaimed successfully",
                     ((Boolean)success).booleanValue());
          
          // "Loop" to make sure each node sees that file is gone
          //   (Runs the same command several times, incrementing currentIndex field)
          currentIndex = 0;
          remote = (PastServiceImpl) pastNodes.elementAt(currentIndex);
          remote.lookup(fileId, new TestCommand() {
            public void receive(Object result) throws Exception {
              assertTrue("PastFunctions", 
                         "File should not be found remotely, node " + currentIndex,
                         result == null);
              
              // Now check if we've visited all nodes
              currentIndex++;
              if (currentIndex < pastNodes.size()) {
                // Perform this check on the next node
                remote = (PastServiceImpl) pastNodes.elementAt(currentIndex);
                remote.lookup(fileId, this);
              } else {
                // We've seen all the nodes, so move on
                
                // Check file does not exist
                local.exists(fileId, new TestCommand() {
                  public void receive(Object exists) throws Exception {
                    assertTrue("PastFunctions", 
                               "File should not exist after delete",
                               !((Boolean)exists).booleanValue());
                    
                    // DONE WITH THE TEST!
                    System.out.println("\n\n---- TestPastFunctions passed! ---------------------\n");
                  }
                });
              }
            }
          });
        }
      });
    }
  }
  

  /**
   * Usage: DistPastTest [-port p] [-bootstrap host[:port]] [-nodes n] [-protocol (rmi|wire)] [-help]
   */
  public static void main(String args[]) {
    parseArgs(args);
    DistPastRegrTest pastTest = new DistPastRegrTest();
    pastTest.start();
  }
  
  /**
   * Common superclass for test commands.
   */
  protected class TestCommand implements Continuation {
    public void receiveResult(Object result) {
      try {
        receive(result);
      }
      catch (Exception e) {
        receiveException(e);
      }
    }
    public void receive(Object result) throws Exception {}
    public void receiveException(Exception e) {
      throw new RuntimeException(e);
    }
  }
  
}
