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

package rice.past.testing;

import rice.Continuation;
import rice.past.*;
import rice.past.messaging.*;

import rice.pastry.PastryNode;
import rice.pastry.dist.*;
import rice.pastry.standard.*;
import rice.pastry.security.*;

import rice.p2p.commonapi.*;
import rice.pastry.commonapi.*;

import rice.persistence.*;

import java.util.*;
import java.net.*;
import java.io.Serializable;

/**
 * @(#) DistPASTRegrTest.java
 *
 * Provides regression testing for the PAST service using distributed nodes.
 *
 * @version $Id$
 * @author Charles Reis
 * @author Alan Mislove
 * 
 * @deprecated This version of PAST has been deprecated - please use the version
 *   located in the rice.p2p.past package.
 */
public class DistPASTRegrTest {
  private DistPastryNodeFactory factory;
  private Vector pastrynodes;
  private Vector pastNodes;

  private Random rng;
  private IPNodeIdFactory idFactory;

  private static int numNodes = 20;
  private static int k = 3;  // replication factor

  private static int port = 5009;
  private static String bshost;
  private static int bsport = 5009;

  private static int protocol = DistPastryNodeFactory.PROTOCOL_RMI;

  private static IdFactory FACTORY = new PastryIdFactory();

  static {
    try {
      bshost = InetAddress.getLocalHost().getHostName();
    } catch (UnknownHostException e) {
      System.out.println("Error determining local host: " + e);
    }
  }

  public DistPASTRegrTest() {
    idFactory = new IPNodeIdFactory(port);
    factory = DistPastryNodeFactory.getFactory(idFactory,
                                               protocol,
                                               port);
    pastrynodes = new Vector();
    pastNodes = new Vector();
    rng = new Random(5);
  }

  /**
   * Gets a handle to a bootstrap node.
   *
   * @return handle to bootstrap node, or null.
   */
  protected NodeHandle getBootstrap() {
    InetSocketAddress address = new InetSocketAddress(bshost, bsport);
    return factory.getNodeHandle(address);
  }

  public synchronized void pause(int ms) {
    System.out.println("waiting for " + (ms/1000) + " sec");
    try { wait(ms); } catch (InterruptedException e) {}
  }



  /* ---------- Setup methods ---------- */

  /**
   * Creates a pastryNode with a PASTService running on it.
   */
  protected PASTService makePASTNode() {
    PastryNode pn = factory.newNode((rice.pastry.NodeHandle) getBootstrap());
    pastrynodes.add(pn);

    StorageManager storage = new StorageManager(FACTORY,
                                                new MemoryStorage(FACTORY),
                                                new LRUCache(new MemoryStorage(FACTORY), 10000));

    PASTServiceImpl past = new PASTServiceImpl(pn, storage, "PAST");
    past.DEBUG = false;
    pastNodes.add(past);
    System.out.println("created " + pn);

    return past;
  }

  /**
   * Creates the nodes used for testing.
   */
  protected void createNodes() {
    for (int i=0; i < numNodes; i++) {
      makePASTNode();
    }
  }

  /**
   * Sets up the environment for regression tests.
   */
  protected void initialize() {
    createNodes();

    // Give nodes a chance to initialize
    System.out.println("DEBUG ---------- Waiting for all nodes to be ready");
    pause(3000);

    Enumeration nodes = pastrynodes.elements();
    while (nodes.hasMoreElements()) {
      PastryNode node = (PastryNode) nodes.nextElement();
      while (!node.isReady()) {
        System.out.println("DEBUG ---------- Waiting for node to be ready");
        pause(2000);
      }
    }
  }


  /* ---------- Testing utility methods ---------- */

  /**
   * Throws an exception if the test condition is not met.
   */
  protected void assertTrue(String name, String intention, boolean test)
    throws TestFailedException
  {
    if (!test) {
       System.exit(0);
      throw new TestFailedException("\nAssertion failed in '" + name +
                                    "'\nExpected: " + intention);
    }
  }

  /**
   * Thows an exception if expected is not equal to actual.
   */
  protected void assertEquals(String name,
                              String description,
                              Object expected,
                              Object actual)
    throws TestFailedException
  {
    if (!expected.equals(actual)) {
      throw new TestFailedException("\nAssertion failed in '" + name +
                                    "'\nDescription: " + description +
                                    "\nExpected: " + expected +
                                    "\nActual: " + actual);
    }
  }


  /* ---------- Test methods and classes ---------- */

  /**
   * Tests routing a PAST request to a particular node.
   */
  protected void testRouteRequest() throws TestFailedException {
    final PASTService local = 
      (PASTService) pastNodes.elementAt(rng.nextInt(numNodes));
    final PASTServiceImpl remote = 
      (PASTServiceImpl) pastNodes.elementAt(rng.nextInt(numNodes));
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
   * Commands for running the PAST functions test.
   */
  protected class TestPASTFunctions {
    final Credentials userCred;
    final PASTService local;
    final Id fileId;
    final String file;
    final String update;
    
    int localCount;
    int currentIndex;
    PASTServiceImpl remote;
    
    /**
     * Sets up this test.
     */
    public TestPASTFunctions() {
      userCred = null;
      local = (PASTService) pastNodes.elementAt(rng.nextInt(numNodes));
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
          assertTrue("PASTFunctions", "Lookup before insert should fail",
                     test == null);
          
          // Should not exists
          local.exists(fileId, new TestCommand() {
            public void receive(Object exists) throws Exception {
              assertTrue("PASTFunctions", "File should not exist before insert",
                         !((Boolean)exists).booleanValue());
              
              // Insert file
              System.out.println("TEST: PASTFunctions: Inserting file with key: " + fileId);
              local.insert(fileId, file,  new TestCommand() {
                public void receive(Object success) throws Exception {
                  assertTrue("PASTFunctions", "Insert of file should succeed",
                             ((Boolean)success).booleanValue());
                  
                  // Should exist
                  local.exists(fileId, new TestCommand() {
                    public void receive(Object exists) throws Exception {
                      assertTrue("PASTFunctions", "File should exist after insert",
                                 ((Boolean)exists).booleanValue());
                      
                      // Try to insert again
                      local.insert(fileId, file,  new TestCommand() {
                        public void receive(Object success) throws Exception {
                          assertTrue("PASTFunctions", 
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
      remote = (PASTServiceImpl) pastNodes.elementAt(currentIndex);
      remote.lookup(fileId, new TestCommand() {
        public void receive(Object res) throws Exception {
          Serializable result = (Serializable) res;
          
          // Check that file is found using PAST, but has no updates
          assertTrue("PASTFunctions", "File should always be found remotely",
                     result != null);
          assertEquals("PASTFunctions", 
                       "Retrieved file should be the same, node " + currentIndex,
                       file, result);

          final TestCommand MONKEY = this;
         
          // Lookup file locally (using Storage)
          remote.getStorage().getObject((rice.pastry.Id) fileId, new TestCommand() {
            public void receive(Object result) throws Exception {
              if (result != null) {
                System.out.println("TEST: Found file locally on node " + currentIndex);
                localCount++;
                assertEquals("PASTFunctions",
                             "Retrieved local file should be the same, node " + currentIndex,
                             file, result);
              }

              // Now check if we've visited all nodes
              currentIndex++;
              if (currentIndex < pastNodes.size()) {
                // Perform this check on the next node
                remote = (PASTServiceImpl) pastNodes.elementAt(currentIndex);
                remote.lookup(fileId, MONKEY);
              } else {
                assertEquals("PASTFunctions",
                             "File should have been found " + k + " time after insert",
                             new Integer(k), new Integer(localCount));

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
          assertTrue("PASTFunctions", "File should be reclaimed successfully",
                     ((Boolean)success).booleanValue());
          
          // "Loop" to make sure each node sees that file is gone
          //   (Runs the same command several times, incrementing currentIndex field)
          currentIndex = 0;
          remote = (PASTServiceImpl) pastNodes.elementAt(currentIndex);
          remote.lookup(fileId, new TestCommand() {
            public void receive(Object result) throws Exception {
              assertTrue("PASTFunctions", 
                         "File should not be found remotely, node " + currentIndex,
                         result == null);
              
              // Now check if we've visited all nodes
              currentIndex++;
              if (currentIndex < pastNodes.size()) {
                // Perform this check on the next node
                remote = (PASTServiceImpl) pastNodes.elementAt(currentIndex);
                remote.lookup(fileId, this);
              } else {
                // We've seen all the nodes, so move on
                
                // Check file does not exist
                local.exists(fileId, new TestCommand() {
                  public void receive(Object exists) throws Exception {
                    assertTrue("PASTFunctions", 
                               "File should not exist after delete",
                               !((Boolean)exists).booleanValue());
                    
                    // DONE WITH THE TEST!
                    System.out.println("\n\n---- TestPASTFunctions passed! ---------------------\n");
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
   * Initializes and runs all regression tests.
   */
  public void runTests() {
    initialize();

    try {
      // Run each test
      testRouteRequest();
      //testPASTFunctions();
      new TestPASTFunctions().start();

      // TO DO:
      //  Test permissions (problems with serializability of dummy credentials?)
      //  Test timeout

      // All tests running...
    }
    catch (TestFailedException e) {
      System.out.println(e.toString());
    }
  }

  /**
   * process command line args
   */
  private static void doInitstuff(String args[]) {
    // process command line arguments

    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-help")) {
        System.out.println("Usage: DistPASTSearchRegrTest [-port p] [-protocol (rmi|wire)] [-bootstrap host[:port]] [-help]");
        System.exit(1);
      }
    }

    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-port") && i+1 < args.length) {
        int p = Integer.parseInt(args[i+1]);
        if (p > 0) port = p;
        break;
      }
    }

    bsport = port;  // make sure bsport = port, if no -bootstrap argument is provided
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-bootstrap") && i+1 < args.length) {
        String str = args[i+1];
        int index = str.indexOf(':');
        if (index == -1) {
          bshost = str;
          bsport = port;
        } else {
          bshost = str.substring(0, index);
          bsport = Integer.parseInt(str.substring(index + 1));
          if (bsport <= 0) bsport = port;
        }
        break;
      }
    }

    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-protocol") && i+1 < args.length) {
        String s = args[i+1];

        if (s.equalsIgnoreCase("wire"))
          protocol = DistPastryNodeFactory.PROTOCOL_WIRE;
        else if (s.equalsIgnoreCase("rmi"))
          protocol = DistPastryNodeFactory.PROTOCOL_RMI;
        else
          System.out.println("ERROR: Unsupported protocol: " + s);

        break;
      }
    }
  }


  /**
   * Usage: DistPASTTest [-port p] [-bootstrap host[:port]] [-nodes n] [-protocol (rmi|wire)] [-help]
   */
  public static void main(String args[]) {

    doInitstuff(args);
    DistPASTRegrTest pastTest = new DistPASTRegrTest();
    pastTest.runTests();

  }


  /**
   * Exception indicating that a regression test failed.
   */
  protected class TestFailedException extends Exception {
    protected TestFailedException(String message) {
      // TO DO: Possible to include stack trace?
      super("\n\n---- Test Failed! --------------------------\n" +
            message +
            "\n\n--------------------------------------------\n");
    }
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
