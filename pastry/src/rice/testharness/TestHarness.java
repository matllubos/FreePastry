/*******************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate

Copyright 2002-2007, Rice University. Copyright 2006-2007, Max Planck Institute 
for Software Systems.  All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither the name of Rice  University (RICE), Max Planck Institute for Software 
Systems (MPI-SWS) nor the names of its contributors may be used to endorse or 
promote products derived from this software without specific prior written 
permission.

This software is provided by RICE, MPI-SWS and the contributors on an "as is" 
basis, without any representations or warranties of any kind, express or implied 
including, but not limited to, representations or warranties of 
non-infringement, merchantability or fitness for a particular purpose. In no 
event shall RICE, MPI-SWS or contributors be liable for any direct, indirect, 
incidental, special, exemplary, or consequential damages (including, but not 
limited to, procurement of substitute goods or services; loss of use, data, or 
profits; or business interruption) however caused and on any theory of 
liability, whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even if 
advised of the possibility of such damage.

*******************************************************************************/ 

package rice.testharness;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.net.InetAddress;
import java.util.Hashtable;
import java.util.Vector;

import rice.environment.logging.Logger;
import rice.p2p.commonapi.Application;
import rice.p2p.commonapi.Endpoint;
import rice.p2p.commonapi.Id;
import rice.p2p.commonapi.IdFactory;
import rice.p2p.commonapi.Message;
import rice.p2p.commonapi.NodeHandle;
import rice.p2p.commonapi.RouteMessage;
import rice.p2p.scribe.Scribe;
import rice.p2p.scribe.ScribeClient;
import rice.p2p.scribe.ScribeContent;
import rice.p2p.scribe.ScribeImpl;
import rice.p2p.scribe.Topic;
import rice.p2p.scribe.ScribePolicy.LimitedScribePolicy;
import rice.pastry.PastryNode;
import rice.pastry.commonapi.PastryIdFactory;
import rice.testharness.messaging.CollectResultsMessage;
import rice.testharness.messaging.CollectResultsResponseMessage;
import rice.testharness.messaging.InitTestMessage;
import rice.testharness.messaging.StartTestMessage;
import rice.testharness.messaging.SubscribedMessage;
import rice.testharness.messaging.UnsubscribedMessage;


/**
 * A TestHarness is a PastryAppl with allows the user to run tests
 * and collect data in a Pastry network.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */
@SuppressWarnings("unchecked")
public class TestHarness implements Application, ScribeClient {
  
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

  private ScribeImpl scribe;
  
  private IdFactory factory;// = new PastryIdFactory();
  
  private Endpoint endpoint;

  Topic topicRoot = new Topic(factory, "ROOT");
  Topic topic = new Topic(factory, "monkey2");
  
  Logger logger;
  
  /**
   * Constructor which creates a TestHarness given a
   * PastryNode.
   *
   * @param pn The PastryNode this TestHarness is running on.
   */
  public TestHarness(PastryNode pn) {
    factory = new PastryIdFactory(pn.getEnvironment());
    logger = pn.getEnvironment().getLogManager().getLogger(TestHarness.class,"monkey");
    endpoint = pn.buildEndpoint(this, "monkey");
    _pastryNode = pn;
    _tests = new Hashtable();
    _files = new Hashtable();
    _streams = new Hashtable();
    _testObjects = new Hashtable();
    _subscribedNodes = new Vector();

    scribe = new ScribeImpl(pn, "monkey");
    scribe.setPolicy(new LimitedScribePolicy(15, pn.getEnvironment()));
    try {
      hostname = InetAddress.getLocalHost().getHostAddress();
    } catch (java.net.UnknownHostException uhe) {
      if (logger.level <= Logger.WARNING) logger.logException("", uhe);
    }
    endpoint.register();
  }
  String hostname;

  public Scribe getScribe() {
    return scribe;
  }
  
  /**
   * This method is called when the TestHarness boots, and it
   * sends a message to the root node (<0x0000..>) informing that
   * node of its presence.
   */
  public void initialize(boolean root) {
    if (root) {
      System.out.println("Joining root scribe group rooted at : " + topicRoot);
      scribe.subscribe(topicRoot, this);
    }

    System.out.println("Anycasting to scribe group rooted at : " + topicRoot);
    SubscribedMessage sm = new SubscribedMessage(_pastryNode.getLocalHandle());
    scribe.publish(topicRoot, new SubscribedMessage(_pastryNode.getLocalHandle()));

    scribe.subscribe(topic, this);
  }

  /**
   * Removes this node from the test harness group
   */
  public void kill() {
    scribe.publish(topicRoot, new UnsubscribedMessage(_pastryNode.getLocalHandle()));
  }

  /**
   * This method is called on the application at the destination node
   * for the given id.
   *
   * @param id The destination id of the message
   * @param message The message being sent
   */
  @SuppressWarnings("unchecked")
  public void deliver(Id id, Message msg) {
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

        // For some reason, this can deadlock.
        //String hostname = InetAddress.getLocalHost().getHostAddress();
        String filename = itm.getTestName() + "-" + itm.getRunName() + "-" + hostname + "-" + _pastryNode.getNodeId().toString().replace('<', '[').replace('>', ']') + ".txt";
        FileOutputStream fw = new FileOutputStream(filename);
        PrintStream ps = new PrintStream(fw);

        Class[] classes = {PrintStream.class, PastryNode.class, TestHarness.class};
        Object[] objects = {ps, _pastryNode, this};

        Test test = (Test) cls.getConstructor(classes).newInstance(objects);

        _testObjects.put(itm.getRunName(), test);
        _files.put(itm.getRunName(), filename);
        _streams.put(itm.getRunName(), fw);

      } catch (InvocationTargetException e) {
        if (logger.level <= Logger.WARNING) logger.logException( 
            "InvocationTargetException occurred during initing of test: " , e.getTargetException());
      } catch (Exception e) {
        if (logger.level <= Logger.WARNING) logger.logException(
            "Exception occurred during initing of test: " , e);
      }
    } else if (msg instanceof StartTestMessage) {
      StartTestMessage stm = (StartTestMessage) msg;
      Test test = (Test) _testObjects.get(stm.getRunName());

      if (test == null) {
        System.out.println("Run " + stm.getRunName() + " not found.");
        return;
      }

      test.startTest(stm.getNodes());
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

        endpoint.route(null, crrm, crm.getSource());

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
        FileWriter fw = new FileWriter(crrm.getRunName() + "-" + crrm.getSource().getId().toString().replace('<', '[').replace('>', ']') + ".txt");
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
    int time = 6000 + (int) (_pastryNode.getEnvironment().getRandomSource().nextDouble() * 4000);

    try { Thread.currentThread().sleep(time); } catch (InterruptedException e) {System.out.println(e);}
  }

  public void sendToAll(ScribeContent m) {
    scribe.publish(topic, m);
  }

  public void send(Message m, NodeHandle nh) {
    endpoint.route(null, m, nh);
  }

  /**
   * This method is invoked when an anycast is received for a topic
   * which this client is interested in.  The client should return
   * whether or not the anycast should continue.
   *
   * @param topic The topic the message was anycasted to
   * @param content The content which was anycasted
   * @return Whether or not the anycast should continue
   */
  public boolean anycast(Topic topic, ScribeContent content) { return true; }

  /**
   * This method is invoked when a message is delivered for a topic this
   * client is interested in.
   *
   * @param topic The topic the message was published to
   * @param content The content which was published
   */
  public void deliver(Topic topic, ScribeContent content) {
    deliver(null, (Message) content);
  }

  /**
   * Informs this client that a child was added to a topic in
   * which it was interested in.
   *
   * @param topic The topic to unsubscribe from
   * @param child The child that was added
   */
  public void childAdded(Topic topic, NodeHandle child) {}

  /**
   * Informs this client that a child was removed from a topic in
   * which it was interested in.
   *
   * @param topic The topic to unsubscribe from
   * @param child The child that was removed
   */
  public void childRemoved(Topic topic, NodeHandle child) {}

  /**
   * Informs the client that a subscribe on the given topic failed
   * - the client should retry the subscribe or take appropriate
   * action.
   *
   * @param topic The topic which the subscribe failed on
   */
  public void subscribeFailed(Topic topic) {}

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
  public boolean forward(RouteMessage message) { return true; }

  /**
   * This method is invoked to inform the application that the given node
   * has either joined or left the neighbor set of the local node, as the set
   * would be returned by the neighborSet call.
   *
   * @param handle The handle that has joined/left
   * @param joined Whether the node has joined or left
   */
  public void update(NodeHandle handle, boolean joined) {}
  
}
