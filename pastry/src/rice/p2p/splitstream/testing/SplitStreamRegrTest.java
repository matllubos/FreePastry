/**
 * "FreePastry" Peer-to-Peer Application Development Substrate Copyright 2002, Rice University. All
 * rights reserved. Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met: - Redistributions of source code
 * must retain the above copyright notice, this list of conditions and the following disclaimer. -
 * Redistributions in binary form must reproduce the above copyright notice, this list of conditions
 * and the following disclaimer in the documentation and/or other materials provided with the
 * distribution. - Neither the name of Rice University (RICE) nor the names of its contributors may
 * be used to endorse or promote products derived from this software without specific prior written
 * permission. This software is provided by RICE and the contributors on an "as is" basis, without
 * any representations or warranties of any kind, express or implied including, but not limited to,
 * representations or warranties of non-infringement, merchantability or fitness for a particular
 * purpose. In no event shall RICE or contributors be liable for any direct, indirect, incidental,
 * special, exemplary, or consequential damages (including, but not limited to, procurement of
 * substitute goods or services; loss of use, data, or profits; or business interruption) however
 * caused and on any theory of liability, whether in contract, strict liability, or tort (including
 * negligence or otherwise) arising in any way out of the use of this software, even if advised of
 * the possibility of such damage.
 */

package rice.p2p.splitstream.testing;
import java.io.Serializable;
import java.net.*;

import java.util.*;

import rice.*;

import rice.p2p.commonapi.*;
import rice.p2p.commonapi.testing.*;
import rice.p2p.splitstream.*;

/**
 * @(#) SplitStreamRegrTest.java Provides regression testing for the Scribe service using distributed
 * nodes.
 *
 * @version $Id$
 * @author Ansley Post 
 */

public class SplitStreamRegrTest extends CommonAPITest {

  // the instance name to use
  /**
   * DESCRIBE THE FIELD
   */
  public static String INSTANCE = "SplitStreamRegrTest";

  // the scribe impls in the ring
  /**
   * DESCRIBE THE FIELD
   */
  protected SplitStreamImpl splitstreams[];

  protected SplitStreamTestClient ssclients[];

  // a random number generator
  /**
   * DESCRIBE THE FIELD
   */
  protected Random rng;

  /**
   * Constructor which sets up all local variables
   */
  public SplitStreamRegrTest() {
    splitstreams = new SplitStreamImpl[NUM_NODES];
    ssclients = new SplitStreamTestClient[NUM_NODES];
    rng = new Random();
  }


  /**
   * Usage: DistScribeRegrTest [-port p] [-bootstrap host[:port]] [-nodes n] [-protocol (rmi|wire)]
   * [-help]
   *
   * @param args DESCRIBE THE PARAMETER
   */
  public static void main(String args[]) {
    parseArgs(args);
    SplitStreamRegrTest splitstreamTest = new SplitStreamRegrTest();
    splitstreamTest.start();
  }

  /**
   * Method which should process the given newly-created node
   *
   * @param node The newly created node
   * @param num The number of this node
   */
  protected void processNode(int num, Node node) {
    splitstreams[num] = new SplitStreamImpl(node, INSTANCE);
    ssclients[num] = new SplitStreamTestClient(node);
  }

  /**
   * Method which should run the test - this is called once all of the nodes have been created and
   * are ready.
   */
  protected void runTest() {
    if (NUM_NODES < 2) {
      System.out.println("The DistScribeRegrTest must be run with at least 2 nodes for proper testing.  Use the '-nodes n' to specify the number of nodes.");
      return;
    }

    // Run each test
    testBasic();
  }

  /*
   *  ---------- Test methods and classes ----------
   */
  /**
   * Tests routing a Past request to a particular node.
   */
  protected void testBasic() {

  }

  /**
   * Private method which generates a random Id
   *
   * @return A new random Id
   */
  private Id generateId() {
    byte[] data = new byte[20];
    new Random().nextBytes(data);
    return FACTORY.buildId(data);
  }
 
  private class SplitStreamTestClient{

   public SplitStreamTestClient(Node n){
      System.out.println("Client Created on Node " + n);
   }
   public void joinFailed(Stripe s){
      System.out.println("Join Failed on Stripe " + s);
   }

   public void deliver(Stripe s, byte[] data){
      System.out.println("Data recieved on Stripe" + s);
  }
 }
}
