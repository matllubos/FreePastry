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

package rice.pastry.socket;

import java.net.InetSocketAddress;

import rice.pastry.NodeHandle;
import rice.pastry.NodeId;
import rice.pastry.client.PastryAppl;
import rice.pastry.dist.DistNodeHandlePool;
import rice.pastry.dist.DistPastryNode;
import rice.pastry.dist.NodeIsDeadException;
import rice.pastry.messaging.Message;
import rice.pastry.messaging.MessageReceiver;
import rice.selector.SelectorManager;
import rice.selector.TimerTask;

/**
 * An Socket-based Pastry node, which has two threads - one thread for
 * performing route set and leaf set maintainance, and another thread for
 * listening on the sockets and performing all non-blocking I/O.
 *
 * @version $Id$
 * @author Alan Mislove
 */
public class SocketPastryNode extends DistPastryNode {
  
  public static final String EC_REASON_MSG_TOO_LARGE = "message size was too large";
  public static final String EC_REASON_CONNECTION_FAULTY = "connection was faulty";
  public static final String EC_REASON_QUEUE_FULL = "too many messages enqueued";

  public static final int EC_MSG_TOO_LARGE = 78001;
  public static final int EC_CONNECTION_FAULTY = 78002;
  public static final int EC_QUEUE_FULL = 78003;


  // The address (ip + port) of this pastry node
  private InetSocketAddress address;

  // The SelectorManager, controlling the selector
  //private SelectorManager manager;

  // The SocketManager, controlling the sockets
  public SocketCollectionManager sManager;

  private PingManager pingManager;

  // The pool of all node handles
  private SocketNodeHandlePool pool;

  /**
   * Constructor
   *
   * @param id The NodeId of this Pastry node.
   */
  public SocketPastryNode(NodeId id) {
    super(id);
  }

  // ************* lifecycle *************
  private boolean alive = true;
  /**
   * Returns false once the node has been killed.
   * @return false when the node has been killed.  True otherwise.
   */
  public boolean isAlive() {
    return alive;
  }

  /**
   * Called after the node is initialized.
   *
   * @param bootstrap The node which this node should boot off of.
   */
  public void doneNode(NodeHandle bootstrap) {
    super.doneNode(bootstrap);

    initiateJoin(bootstrap);
    //manager.run();
  }

  /**
   * Method for simulating the death of this node. Should only be used for
   * testing purposes.
   */
  public void kill() {
//    System.out.println("SPN.kill()");
    alive = false;
    super.kill();
    sManager.kill();
    pingManager.kill();
  }

  // ***************** Error Handling ********************  
  /** 
   * Returns the available error code strings.
   */
  public String getErrorString(int errorCode) {
    switch (errorCode) {
      case EC_MSG_TOO_LARGE:
        return EC_REASON_MSG_TOO_LARGE;
      case EC_CONNECTION_FAULTY:
        return EC_REASON_CONNECTION_FAULTY;
      case EC_QUEUE_FULL:
        return EC_REASON_QUEUE_FULL;
    }
    return super.getErrorString(errorCode);      
  }

  /**
   * Helper to notify the app that the message couldn't be sent for the specified reason.
   */
  public void messageNotSent(Message m, int errorCode) {
    MessageReceiver mr = getMessageDispatch().getDestination(m);
    if ((mr != null) && (mr instanceof PastryAppl)) {
      PastryAppl a = (PastryAppl)mr;
      a.messageNotDelivered(m, errorCode);
    } else {
      if ((errorCode == EC_CONNECTION_FAULTY) && (Thread.currentThread() == SelectorManager.getSelectorManager())) {
        // don't print anything 
      } else {
        System.out.println("WARNING: message not sent "+m+":"+getErrorString(errorCode));    
        //Thread.dumpStack();
      }
    }
  }

  // ***************** Scheduling **************
  /**
   * DESCRIBE THE METHOD
   *
   * @param task DESCRIBE THE PARAMETER
   * @param delay DESCRIBE THE PARAMETER
   */
  protected void scheduleTask(TimerTask task, long delay) {
    try {
      timer.schedule(task, delay);
    } catch (IllegalStateException ise) {
      if (isAlive()) {
        throw ise;
      } else {
        throw new NodeIsDeadException(ise);
      }
    }
  }

  /**
   * DESCRIBE THE METHOD
   *
   * @param task DESCRIBE THE PARAMETER
   * @param delay DESCRIBE THE PARAMETER
   * @param period DESCRIBE THE PARAMETER
   */
  protected void scheduleTask(TimerTask task, long delay, long period) {
    try {
      timer.schedule(task, delay, period);
    } catch (IllegalStateException ise) {
      if (isAlive()) {
        throw ise;
      } else {
        throw new NodeIsDeadException(ise);
      }
    }
  }  

  // ************* Accessors **************
  /**
   * Returns the SocketManager for this pastry node.
   *
   * @return The SocketManager for this pastry node.
   */
  public SocketCollectionManager getSocketCollectionManager() {
    return sManager;
  }

  /**
   * Returns the PingManager for this pastry node.
   *
   * @return The PingManager for this pastry node.
   */
  public PingManager getPingManager() {
    return pingManager;
  }

  /**
   * Returns the DistNodeHandlePool for this pastry node.
   *
   * @return the SocketNodeHandlePool for this pastry node.
   */
  public DistNodeHandlePool getNodeHandlePool() {
    return pool;
  }

  /**
   * Setter for pool
   * @param pool
   */
  public void setSocketNodeHandlePool(SocketNodeHandlePool pool) {
    this.pool = pool;    
  }


  /**
   * Helper method which allows the WirePastryNodeFactory to initialize a number
   * of the pastry node's elements.
   *
   * @param address The address of this pastry node.
   * @param manager The socket manager for this pastry node.
   * @param lsmf Leaf set maintenance frequency. 0 means never.
   * @param rsmf Route set maintenance frequency. 0 means never.
   * @param sManager The new SocketElements value
   * @param pingManager The new SocketElements value
   * @param pool The new SocketElements value
   */
  public void setSocketElements(InetSocketAddress address,                                
                                SocketCollectionManager sManager,
                                PingManager pingManager,
                                int lsmf,
                                int rsmf) {
    this.address = address;
    this.sManager = sManager;
    this.pingManager = pingManager;
    this.leafSetMaintFreq = lsmf;
    this.routeSetMaintFreq = rsmf;
  }
  

  // ********************* Debugging ***************
  /**
   * Prints out a String representation of this node
   *
   * @return a String
   */
  public String toString() {
    return "SocketPastryNode (" + getNodeId() + ")";
  }  
}
