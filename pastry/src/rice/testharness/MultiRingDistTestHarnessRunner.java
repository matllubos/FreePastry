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

package rice.testharness;

import rice.pastry.client.*;
import rice.pastry.security.*;
import rice.pastry.messaging.*;
import rice.pastry.routing.*;
import rice.pastry.*;
import rice.pastry.direct.*;
import rice.pastry.dist.*;
import rice.pastry.wire.*;
import rice.pastry.multiring.*;
import rice.pastry.standard.*;

import rice.scribe.*;
import rice.scribe.messaging.*;

import rice.testharness.messaging.*;

import java.util.*;
import java.io.*;
import java.net.*;


/**
 * A TestHarness is a PastryAppl with allows the user to run tests,
 * collect data, and publish new versions of the TestHarness
 * in a Pastry network.  This version is designed to support pastry
 * nodes in multiple rings.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */
public class MultiRingDistTestHarnessRunner extends DistTestHarnessRunner {

  public static int MULTI_RING_START_PORT;
  
  protected Vector otherPastryNodes;
  protected Vector otherTestNodes;

  protected MultiRingPastryNodeFactory multiRingFactory;

  /**
   * Constructor which creates a TestHarness given a
   * PastryNode.
   *
   * @param pn The PastryNode this TestHarness is running on.
   */
  public MultiRingDistTestHarnessRunner() {
    super();

    otherPastryNodes = new Vector();
    otherTestNodes = new Vector();
    multiRingFactory = new MultiRingPastryNodeFactory(factory);
  }

  protected PastryNode createPastryNode() {
    PastryNode pn = multiRingFactory.newNode(getPastryBootstrap());

    return pn;
  }

  protected NodeHandle getPastryBootstrap() {
    if (pastryNodes.size() == 0) {
      return super.getPastryBootstrap();
    } else {
      MultiRingPastryNode node = (MultiRingPastryNode) pastryNodes.elementAt(pastryNodes.size() - 1);
      return factory.getNodeHandle(((DistNodeHandle) node.getPastryNode().getLocalHandle()).getAddress());
    }
  }

  private void duplicateNode(final int num) {
    int port = MULTI_RING_START_PORT + num;
    
    PastryNode pn = multiRingFactory.joinRing((MultiRingPastryNode) pastryNodes.elementAt(num), null);

    System.err.println("Created node " + num + " " + pn.getNodeId() + " on port " + port);

    TestHarness test = new TestHarness(pn);

    while (! pn.isReady()) {
      pause(500);
    }

    test.initialize();

    otherPastryNodes.addElement(pn);
    otherTestNodes.addElement(test);
  }
  
  
  public static void processArgs(String[] args) {
    DistTestHarnessRunner.processArgs(args);
    
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-port2") && i+1 < args.length) {
        int n = Integer.parseInt(args[i+1]);
        if (n > 0) MULTI_RING_START_PORT = n;
        break;
      }
    }
  }
  
  /**
   * Initializes the TestHarnesss are waits for input.
   */
  public static void main(String[] args) {
    processArgs(args);

    MultiRingDistTestHarnessRunner runner = new MultiRingDistTestHarnessRunner();
    runner.run();
  }

  /* Basically a big switch/case for reading the input and acting accordingly
   */
  protected boolean parseInput( String in ) {
    boolean result = super.parseInput(in);
    
    try {
      StringTokenizer tokened = new StringTokenizer( in, " \t\n" );
      
      if( !tokened.hasMoreTokens() ) { return false; }

      String token = tokened.nextToken();

      if ( token.startsWith( "dupnode" ) ) {
        if( !tokened.hasMoreTokens() ) { return false; }
        
        int num = Integer.parseInt(tokened.nextToken());
        duplicateNode(num);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    
    return result;
  }

  public static synchronized void pause(int ms) {
    System.err.println("waiting for " + (ms/1000) + " sec");
    System.out.println("waiting for " + (ms/1000) + " sec");
    try { Thread.currentThread().sleep(ms); } catch (InterruptedException e) {}
  }

  public static synchronized void pauseQuiet(int ms) {
    try { Thread.currentThread().sleep(ms); } catch (InterruptedException e) {}
  }  
}
