/**
 * "FreePastry" Peer-to-Peer Application Development Substrate Copyright 2002,
 * Rice University. All rights reserved. Redistribution and use in source and
 * binary forms, with or without modification, are permitted provided that the
 * following conditions are met: - Redistributions of source code must retain
 * the above copyright notice, this list of conditions and the following
 * disclaimer. - Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution. -
 * Neither the name of Rice University (RICE) nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission. This software is provided by RICE and the
 * contributors on an "as is" basis, without any representations or warranties
 * of any kind, express or implied including, but not limited to,
 * representations or warranties of non-infringement, merchantability or fitness
 * for a particular purpose. In no event shall RICE or contributors be liable
 * for any direct, indirect, incidental, special, exemplary, or consequential
 * damages (including, but not limited to, procurement of substitute goods or
 * services; loss of use, data, or profits; or business interruption) however
 * caused and on any theory of liability, whether in contract, strict liability,
 * or tort (including negligence or otherwise) arising in any way out of the use
 * of this software, even if advised of the possibility of such damage.
 */

package rice.pastry.dist;

import java.net.*;
import java.util.*;
import rice.pastry.*;
import rice.pastry.join.*;
import rice.pastry.leafset.*;
import rice.pastry.messaging.*;
import rice.pastry.routing.*;
import sun.misc.SignalHandler;

/**
 * Class which represents the abstraction of a "real" pastry node. Designed to
 * be extended by the protocol implementation (i.e. RMI or Socket) desired.
 *
 * @version $Id$
 * @author Alan Mislove
 */

public abstract class DistPastryNode extends PastryNode {

  // Period (in seconds) at which the leafset and routeset maintenance tasks, respectively, are invoked.
  // 0 means never.
  /**
   * DESCRIBE THE FIELD
   */
  protected int leafSetMaintFreq, routeSetMaintFreq;

  // timer that supports scheduled messages
  /**
   * DESCRIBE THE FIELD
   */
  protected static final Timer timer = new Timer(true);
  
  // the list of network listeners
  private Vector listeners;

  // the list of errors
  private static Vector errors = new Vector();

  // join retransmission stuff
  private ScheduledMessage joinEvent;

  /**
   * Constructor, with NodeId. Need to set the node's ID before this node is
   * inserted as localHandle.localNode.
   *
   * @param id DESCRIBE THE PARAMETER
   */
  protected DistPastryNode(NodeId id) {    
    super(id);
    SignalHandler s;
    
//    timer = new Timer(true);
    // uses deamon thread, so it terminates once other threads have terminated
    
    this.listeners = new Vector();
  }
  
  public void addNetworkListener(NetworkListener listener) {
    listeners.add(listener);
  }
  
  protected NetworkListener[] getNetworkListeners() {
    return (NetworkListener[]) listeners.toArray(new NetworkListener[0]);
  }
  
  public void broadcastSentListeners(Object message, InetSocketAddress address, int size) {
    NetworkListener[] listeners = getNetworkListeners();
    
    for (int i=0; i<listeners.length; i++)
      listeners[i].dataSent(message, address, size);
  }
  
  public void broadcastReceivedListeners(Object message, InetSocketAddress address, int size) {
    NetworkListener[] listeners = getNetworkListeners();
    
    for (int i=0; i<listeners.length; i++)
      listeners[i].dataReceived(message, address, size);
  }

  public static String[] getErrors() {
    String[] result = (String[]) errors.toArray(new String[0]);
    errors.clear();
    
    return result;
  }
  
  public static void addError(String error) {
    if (errors.size() > 20)
      errors.removeElementAt(0);
    
    errors.add(error);
  }

  /**
   * Method which returns the Dist for this Pastry node.
   *
   * @return The node handle pool for this pastry node.
   */
  public abstract DistNodeHandlePool getNodeHandlePool();


  /**
   * Sends an InitiateJoin message to itself.
   *
   * @param bootstrap Node handle to bootstrap with.
   */
  public final void initiateJoin(NodeHandle bootstrap) {
    //System.out.println("DistPN.initiateJoin()");
    if (bootstrap != null) {
      //this.receiveMessage(new InitiateJoin(bootstrap));

      // schedule (re-)transmission of the join message, every 5s
      //joinEvent = scheduleMsg(new InitiateJoin(bootstrap), 0, 5000);

      // schedule (re-)transmission of the join message at an exponential backoff
      joinEvent = scheduleMsgExpBackoff(new InitiateJoin(bootstrap), 0, 5000, 2);

    } else {
      setReady();
    }
    // no bootstrap node, so ready immediately
  }


  /**
   * Called from PastryNode when the join succeeds.
   */
  public void nodeIsReady() {
    if (joinEvent != null) {
      joinEvent.cancel();
    }
    // cancel join retransmissions
  }


  /**
   * Called after the node is initialized.
   *
   * @param bootstrap DESCRIBE THE PARAMETER
   */
  public void doneNode(NodeHandle bootstrap) {

    if (leafSetMaintFreq > 0) {
      // schedule the leafset maintenance event
      scheduleMsgAtFixedRate(new InitiateLeafSetMaintenance(),
        leafSetMaintFreq * 1000, leafSetMaintFreq * 1000);
    }
    if (routeSetMaintFreq > 0) {
      // schedule the routeset maintenance event
      scheduleMsgAtFixedRate(new InitiateRouteSetMaintenance(),
        routeSetMaintFreq * 1000, routeSetMaintFreq * 1000);
    }
  }


  /**
   * Method which kills a PastryNode (used only for testing).
   */
  public void kill() {
    // cancel all scheduled messages
    //timer.cancel();
  }


  /**
   * Schedule the specified message to be sent to the local node after a
   * specified delay. Useful to provide timeouts.
   *
   * @param msg a message that will be delivered to the local node after the
   *      specified delay
   * @param delay time in milliseconds before message is to be delivered
   * @return the scheduled event object; can be used to cancel the message
   */
  public ScheduledMessage scheduleMsg(Message msg, long delay) {
    ScheduledMessage sm = new ScheduledMessage(this, msg);
    timer.schedule(sm, delay);
    return sm;
  }


  /**
   * Schedule the specified message for repeated fixed-delay delivery to the
   * local node, beginning after the specified delay. Subsequent executions take
   * place at approximately regular intervals separated by the specified period.
   * Useful to initiate periodic tasks.
   *
   * @param msg a message that will be delivered to the local node after the
   *      specified delay
   * @param delay time in milliseconds before message is to be delivered
   * @param period time in milliseconds between successive message deliveries
   * @return the scheduled event object; can be used to cancel the message
   */
  public ScheduledMessage scheduleMsg(Message msg, long delay, long period) {
    ScheduledMessage sm = new ScheduledMessage(this, msg);
    timer.schedule(sm, delay, period);
    return sm;
  }

  public ExponentialBackoffScheduledMessage scheduleMsgExpBackoff(Message msg, long delay, long initialPeriod, double expBase) {
    ExponentialBackoffScheduledMessage sm = new ExponentialBackoffScheduledMessage(this,msg,timer,delay,initialPeriod,expBase);
    return sm;
  }

  /**
   * Schedule the specified message for repeated fixed-rate delivery to the
   * local node, beginning after the specified delay. Subsequent executions take
   * place at approximately regular intervals, separated by the specified
   * period.
   *
   * @param msg a message that will be delivered to the local node after the
   *      specified delay
   * @param delay time in milliseconds before message is to be delivered
   * @param period time in milliseconds between successive message deliveries
   * @return the scheduled event object; can be used to cancel the message
   */
  public ScheduledMessage scheduleMsgAtFixedRate(Message msg, long delay, long period) {
    ScheduledMessage sm = new ScheduledMessage(this, msg);
    timer.scheduleAtFixedRate(sm, delay, period);
    return sm;
  }

}

