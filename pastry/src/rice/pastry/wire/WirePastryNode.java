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

package rice.pastry.wire;
import java.io.*;
import java.net.*;

import java.util.*;

import rice.pastry.*;
import rice.pastry.client.*;
import rice.pastry.dist.*;
import rice.pastry.join.*;
import rice.pastry.leafset.*;
import rice.pastry.messaging.*;
import rice.pastry.routing.*;

/**
 * An Socket-based Pastry node, which has two threads - one thread for
 * performing route set and leaf set maintainance, and another thread for
 * listening on the sockets and performing all non-blocking I/O.
 *
 * @version $Id$
 * @author Alan Mislove
 */

public class WirePastryNode extends DistPastryNode {

  // The address (ip + port) of this pastry node
  private InetSocketAddress _address;

  // The SelectorManager, controlling the selector
  private SelectorManager _manager;

  // The DatagramManager, controlling the datagrams
  private DatagramManager _dManager;

  // The SocketManager, controlling the sockets
  private SocketManager _sManager;

  // The pool of all node handles
  private WireNodeHandlePool _pool;

  // The thread in which the SelectorManager is running
  private Thread _executionThread;


  /**
   * Constructor
   *
   * @param id The NodeId of this Pastry node.
   */
  public WirePastryNode(NodeId id) {
    super(id);
  }

  /**
   * Returns the SelectorManager for this pastry node.
   *
   * @return The SelectorManager for this pastry node.
   */
  public SelectorManager getSelectorManager() {
    return _manager;
  }

  /**
   * Returns the DatagramManager for this pastry node.
   *
   * @return The DatagramManager for this pastry node.
   */
  public DatagramManager getDatagramManager() {
    return _dManager;
  }

  /**
   * Returns the SocketManager for this pastry node.
   *
   * @return The SocketManager for this pastry node.
   */
  public SocketManager getSocketManager() {
    return _sManager;
  }

  /**
   * Returns the WireNodeHandlePool for this pastry node.
   *
   * @return The WireNodeHandlePool for this pastry node.
   */
  public DistNodeHandlePool getNodeHandlePool() {
    return _pool;
  }

  /**
   * Helper method which allows the WirePastryNodeFactory to initialize a number
   * of the pastry node's elements.
   *
   * @param address The address of this pastry node.
   * @param manager The socket manager for this pastry node.
   * @param pool The node handle pool for this pastry node.
   * @param lsmf Leaf set maintenance frequency. 0 means never.
   * @param rsmf Route set maintenance frequency. 0 means never.
   * @param dManager The new SocketElements value
   * @param sManager The new SocketElements value
   */
  public void setSocketElements(InetSocketAddress address,
                                SelectorManager manager,
                                DatagramManager dManager,
                                SocketManager sManager,
                                WireNodeHandlePool pool,
                                int lsmf,
                                int rsmf) {
    _address = address;
    _manager = manager;
    _dManager = dManager;
    _sManager = sManager;
    _pool = pool;
    leafSetMaintFreq = lsmf;
    routeSetMaintFreq = rsmf;
  }

  /**
   * Sets the thread which the pastry node is running in.
   *
   * @param t The thread
   */
  public void setThread(Thread t) {
    _executionThread = t;
  }

  /**
   * Checks whether the current thread is the execution thread.
   *
   * @return whether or not the current thread is the executing thread
   */
  public boolean inThread() {
    return _executionThread.equals(Thread.currentThread());
  }

  /**
   * Called after the node is initialized.
   *
   * @param bootstrap The node which this node should boot off of.
   */
  public void doneNode(NodeHandle bootstrap) {
    super.doneNode(bootstrap);

    //if (leafSetMaintFreq > 0 || routeSetMaintFreq > 0)
    //new Thread(new MaintThread()).start();

    initiateJoin(bootstrap);
    _manager.run();
  }

  /**
   * DESCRIBE THE METHOD
   */
  public void nodeIsReady() {
    super.nodeIsReady();
  }

  /**
   * Method for simulating the death of this node. Should only be used for
   * testing purposes.
   */
  public void kill() {
    super.kill();
    _manager.kill();
  }
}
