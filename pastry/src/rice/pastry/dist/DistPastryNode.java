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

import java.net.InetSocketAddress;
import java.util.*;

import rice.*;
import rice.pastry.ExponentialBackoffScheduledMessage;
import rice.pastry.NetworkListener;
import rice.pastry.NodeHandle;
import rice.pastry.NodeId;
import rice.pastry.PastryNode;
import rice.pastry.ScheduledMessage;
import rice.pastry.join.InitiateJoin;
import rice.pastry.leafset.InitiateLeafSetMaintenance;
import rice.pastry.messaging.Message;
import rice.pastry.routing.InitiateRouteSetMaintenance;
import rice.selector.SelectorManager;
import rice.selector.Timer;
import sun.misc.SignalHandler;

/**
 * Class which represents the abstraction of a "real" pastry node. Designed to
 * be extended by the protocol implementation (i.e. RMI or Socket) desired.
 *
 * @version $Id$
 * @author Alan Mislove
 */

public abstract class DistPastryNode extends PastryNode {
  
  // the queue used for processing requests
  public static ProcessingQueue QUEUE = new ProcessingQueue();
  public static ProcessingThread THREAD = new ProcessingThread(QUEUE);
  
  static {
    THREAD.start();
  }
  
  // Period (in seconds) at which the leafset and routeset maintenance tasks, respectively, are invoked.
  // 0 means never.
  protected int leafSetMaintFreq, routeSetMaintFreq;

  // timer that supports scheduled messages
  protected static final Timer timer = SelectorManager.getSelectorManager().getTimer();//new Timer(true);
  
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
  
  public Timer getTimer() {
    return timer;
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

	protected ScheduledMessage leafSetRoutineMaintenance = null;
	protected ScheduledMessage routeSetRoutineMaintenance = null;

  /**
   * Called after the node is initialized.
   *
   * @param bootstrap DESCRIBE THE PARAMETER
   */
  public void doneNode(NodeHandle bootstrap) {

    if (routeSetMaintFreq > 0) {
      // schedule the routeset maintenance event
      routeSetRoutineMaintenance = scheduleMsgAtFixedRate(new InitiateRouteSetMaintenance(),
        routeSetMaintFreq * 1000, routeSetMaintFreq * 1000);
//        System.out.println("Scheduling routeSetMaint for "+routeSetMaintFreq * 1000+","+routeSetMaintFreq * 1000);
    }
    if (leafSetMaintFreq > 0) {
      // schedule the leafset maintenance event
      leafSetRoutineMaintenance = scheduleMsgAtFixedRate(new InitiateLeafSetMaintenance(),
        leafSetMaintFreq * 1000, leafSetMaintFreq * 1000);
//        System.out.println("Scheduling leafSetMaint for "+leafSetMaintFreq * 1000+","+leafSetMaintFreq * 1000);
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
  
  /**
   * Schedules a job for processing on the dedicated processing thread.  CPU intensive jobs, such
   * as encryption, erasure encoding, or bloom filter creation should never be done in the context
   * of the underlying node's thread, and should only be done via this method.  
   *
   * @param task The task to run on the processing thread
   * @param command The command to return the result to once it's done
   */
  public void process(Executable task, Continuation command) {
    QUEUE.enqueue(new ProcessingRequest(task, command));
  }
  
  private static class ProcessingThread extends Thread {
    ProcessingQueue queue;
	   
	   public ProcessingThread(ProcessingQueue queue){
       super("Dedicated Processing Thread");
       this.queue = queue;
	   }
	   
	   public void run() {
       while (true) {
         ProcessingRequest e = queue.dequeue();
         
         System.out.println("COUNT: " + System.currentTimeMillis() + " Starting execution of " + e.r);
         e.run();
         System.out.println("COUNT: " + System.currentTimeMillis() + " Done execution of " + e.r);
       }
	   }
  }
  
  public static class ProcessingQueue {
    
    List q = new LinkedList();
	  int capacity = -1;
	  
	  public ProcessingQueue() {
	     /* do nothing */
	  }
	  
	  public ProcessingQueue(int capacity) {
	     this.capacity = capacity;
	  }
    
    public synchronized int getLength() {
      return q.size();
    }
	  
	  public synchronized void enqueue(ProcessingRequest request) {
      if (capacity < 0 || q.size() < capacity) {
			  q.add(request);
			  notifyAll();
		  } else {
			  request.returnError(new ProcessingQueueOverflowException());
      }
	  }
	  
	  public synchronized ProcessingRequest dequeue() {
      while (q.isEmpty()) {
        try {
          wait();
        } catch (InterruptedException e) {
        }
      }
      
      return (ProcessingRequest) q.remove(0);
    }
	}

  private static class ProcessingRequest {
    Continuation c;
    Executable r;
    
		public ProcessingRequest(Executable r, Continuation c){
      this.r = r;
      this.c = c;
		}
    
    public void returnResult(Object o) {
      c.receiveResult(o); 
    }
    
    public void returnError(Exception e) {
      c.receiveException(e); 
    }
    
    public void run() {
      try {
        final Object result = r.execute();
        
        SelectorManager.getSelectorManager().invoke(new Runnable() {
          public void run() {
            returnResult(result);
          }
        });
      } catch (final Exception e) {
        SelectorManager.getSelectorManager().invoke(new Runnable() {
          public void run() {
            returnError(e);
          }
        });
      }
    }
	}
  
  public static class ProcessingQueueOverflowException extends Exception {
  }
}

