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
import rice.pastry.rmi.*;
import rice.pastry.standard.*;

import rice.testharness.messaging.*;

import java.util.*;
import java.io.*;
import java.net.*;
import java.rmi.*;
import java.lang.reflect.*;


/**
 * A TestHarness is a PastryAppl with allows the user to run tests
 * and collect data in a Pastry network.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */

public class TestHarness extends PastryAppl implements Serializable {

  public static final NodeId ROOT_NODE_ID = new NodeId(new byte[NodeId.nodeIdBitLength]);
  
  /**
   * The credentials for the TestHarness system.
   */
  final protected Credentials _credentials = new PermissiveCredentials();

  /**
   * The PastryNode for this TestHarness node.
   */
  public PastryNode _pastryNode;

  /**
   * The NodeID of the SCRIBE group for the overall test harness
   * system.
   */
  public Vector _subscribedNodes;

  /**
   * Hashtables containing references to the URLs, filenames, and
   * PrintWriters for all of the tests.
   */
  public Hashtable _tests;
  public Hashtable _files;
  public Hashtable _streams;
  public Hashtable _testObjects;

  /**
   * The name of the current membership poll.
   */
  private String _currentPollName;

  private Random _random;

  /**
   * Constructor which creates a TestHarness given a
   * PastryNode.
   *
   * @param pn The PastryNode this TestHarness is running on.
   */
  public TestHarness(PastryNode pn) {
    super(pn);
    _pastryNode = pn;
    _tests = new Hashtable();
    _files = new Hashtable();
    _streams = new Hashtable();
    _random = new Random();
    _testObjects = new Hashtable();
    _subscribedNodes = new Vector();
  }

  /**
   * Returns the address of this application.
   *
   * @return the address.
   */
  public Address getAddress() {
    return TestHarnessAddress.instance();
  }

  /**
   * Returns the credentials of this application.
   *
   * @return the credentials.
   */
  public Credentials getCredentials() {
    return _credentials;
  }

  /**
   * This method is called when the TestHarness boots, and it
   * sends a message to the root node (<0x0000..>) informing that
   * node of its presence.
   */
  public void initialize() {
    SubscribedMessage sm = new SubscribedMessage(_pastryNode.getLocalHandle());
    routeMsg(ROOT_NODE_ID, sm, _credentials, null);
  }

  /**
   * Removes this node from the test harness group
   */
  public void kill() {
    UnsubscribedMessage um = new UnsubscribedMessage(_pastryNode.getNodeId());
    routeMsg(ROOT_NODE_ID, um, _credentials, null);
  }

  /**
   * Called by Scribe on a PUBLISH event when a message arrives to its
   * destination.
   *
   * @param msg
   * The message sent in the PUBLISH message.
   */
  public void messageForAppl(Message msg) {
    if (msg instanceof SubscribedMessage) {
      _subscribedNodes.addElement(((SubscribedMessage) msg).getSource());
    } else if (msg instanceof UnsubscribedMessage) {
      _subscribedNodes.remove(((UnsubscribedMessage) msg).getSource());
    } else if (msg instanceof InitTestMessage) {

      InitTestMessage itm = (InitTestMessage) msg;
      System.out.println("Initing test " + itm.getTestName());

      try {
        Class cls = Class.forName(itm.getTestName());

        if (! (Test.class.isAssignableFrom(cls))) {
         System.out.println("Class " + itm.getTestName() + " is not a Test class.");
         return;
        }

        String hostname = InetAddress.getLocalHost().getHostAddress();
        String filename = itm.getTestName() + "-" + itm.getRunName() + "-" + hostname + "-" + _pastryNode.getNodeId().toString().replace('<', '[').replace('>', ']') + ".txt";
        FileOutputStream fw = new FileOutputStream(filename);
        PrintStream ps = new PrintStream(fw);

        Class[] classes = {PrintStream.class, PastryNode.class};
        Object[] objects = {ps, _pastryNode};

        Test test = (Test) cls.getConstructor(classes).newInstance(objects);

        _testObjects.put(itm.getRunName(), test);
        _files.put(itm.getRunName(), filename);
        _streams.put(itm.getRunName(), fw);

      } catch (Exception e) {
        System.out.println("Exception occurred during initing of test: " + e);
      }
    } else if (msg instanceof StartTestMessage) {
      StartTestMessage stm = (StartTestMessage) msg;
      Test test = (Test) _testObjects.get(stm.getRunName());

      if (test == null) {
        System.out.println("Run " + stm.getRunName() + " not found.");
        return;
      }

      test.startTest(this, stm.getNodes());
    } else if (msg instanceof CollectResultsMessage) {
      CollectResultsMessage crm = (CollectResultsMessage) msg;

      try {
        FileOutputStream fw = (FileOutputStream) _streams.get(crm.getRunName());

        if (fw == null) {
          System.out.println("Run " + crm.getRunName() + " not found.");
          return;
        }

        fw.close();

        File file = new File((String) _files.get(crm.getRunName()));

        String result = "";

        BufferedReader reader = new BufferedReader(new FileReader(file));
        String input = reader.readLine();

        while(input!=null){
          result += input + "\n";
          input = reader.readLine();
        }

        CollectResultsResponseMessage crrm = new CollectResultsResponseMessage(_pastryNode.getLocalHandle(),
                                                                               crm.getRunName(),
                                                                               result);

        routeMsgDirect(crm.getSource(), crrm, _credentials, null);

        reader.close();
        file.delete();
      } catch(FileNotFoundException e) {
        System.out.println("FileNotFoundException occurred during collection:" + e);
      } catch(IOException e) {
        System.out.println("IOException occurred during collection:" + e);
      }
    } else if (msg instanceof CollectResultsResponseMessage) {
      CollectResultsResponseMessage crrm = (CollectResultsResponseMessage) msg;

      try {
        FileWriter fw = new FileWriter(crrm.getRunName() + "-" + crrm.getSource().getNodeId().toString().replace('<', '[').replace('>', ']') + ".txt");
        fw.write(crrm.getResults());
        fw.close();
      } catch (Exception e) {
        System.out.println("Exception writing output file: " + e);
      }
    }
  }


  /**
   * Pauses for a random time between 2 and 5 seconds.
   */
  public void pauseRandom() {
    int time = 6000 + (int) (_random.nextDouble() * 4000);

    try { Thread.currentThread().sleep(time); } catch (InterruptedException e) {System.out.println(e);}
  }

  public void sendToAll(final Message m) {
    Thread t = new Thread() {
      public void run() {
        for (int i=0; i<_subscribedNodes.size(); i++) {
          routeMsgDirect((NodeHandle) _subscribedNodes.elementAt(i), m, new PermissiveCredentials(), null);
        }
      }
    };

    t.start();
  }

  public void send(final Message m, final NodeHandle nh) {
    Thread t = new Thread() {
      public void run() {
        routeMsgDirect(nh, m, _credentials, null);
      }
    };

    t.start();
  }
}
