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

package rice.pastry.socket;

import rice.pastry.*;
import rice.pastry.dist.*;
import rice.pastry.leafset.*;
import rice.pastry.routing.*;
import rice.pastry.messaging.*;
import rice.pastry.join.*;
import rice.pastry.client.*;

import java.util.*;
import java.io.*;
import java.net.*;

/**
 * An Socket-based Pastry node, which has two threads - one thread
 * for performing route set and leaf set maintainance, and another
 * thread for listening on the sockets and performing all non-blocking
 * I/O.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */

public class SocketPastryNode extends DistPastryNode {

  // The address (ip + port) of this pastry node
  private InetSocketAddress _address;

  // Large value (in seconds) means infrequent, 0 means never.
  private int leafSetMaintFreq, routeSetMaintFreq;

  // The SocketManager, controlling the sockets
  private SocketManager _manager;

  // The pool of all node handles
  private SocketNodeHandlePool _pool;

  private class MaintThread implements Runnable {
    public void run() {

      int leaftime = 0, routetime = 0, slptime;

      if (leafSetMaintFreq == 0)
        slptime = routeSetMaintFreq;
      else if (routeSetMaintFreq == 0)
        slptime = leafSetMaintFreq;
      else if (leafSetMaintFreq < routeSetMaintFreq)
        slptime = leafSetMaintFreq;
      else
        slptime = routeSetMaintFreq;

      // Assumes one of leafSetMaintFreq and routeSetMaintFreq is a
      // multiple of the other. Generally true; else it approximates
      // the larger one to the nearest upward multiple.

      while (true) {
        try {
          Thread.sleep(1000 * slptime);
        } catch (InterruptedException e) {}

        leaftime += slptime;
        routetime += slptime;

        if (leafSetMaintFreq != 0 && leaftime >= leafSetMaintFreq) {
          leaftime = 0;
          receiveMessage(new InitiateLeafSetMaintenance());
        }

        if (routeSetMaintFreq != 0 && routetime >= routeSetMaintFreq) {
          routetime = 0;
          receiveMessage(new InitiateRouteSetMaintenance());
        }
      }
    }
  }

  /**
   * Constructor
   *
   * @param id The NodeId of this Pastry node.
   */
  public SocketPastryNode(NodeId id) {
    super(id);
  }

  /**
   * Helper method which allows the SocketPastryNodeFactory to
   * initialize a number of the pastry node's elements.
   *
   * @param address The address of this pastry node.
   * @param manager The socket manager for this pastry node.
   * @param pool The node handle pool for this pastry node.
   * @param lsmf Leaf set maintenance frequency. 0 means never.
   * @param rsmf Route set maintenance frequency. 0 means never.
   */
  public void setSocketElements(InetSocketAddress address,
                                SocketManager manager,
                                SocketNodeHandlePool pool,
                                int lsmf,
                                int rsmf) {
    _address = address;
    _manager = manager;
    _pool = pool;
    leafSetMaintFreq = lsmf;
    routeSetMaintFreq = rsmf;
  }

  /**
   * Called after the node is initialized.
   *
   * @param bootstrap The node which this node should boot off of.
   */
  public void doneNode(final NodeHandle bootstrap) {
    if (leafSetMaintFreq > 0 || routeSetMaintFreq > 0)
      new Thread(new MaintThread()).start();

    initiateJoin(bootstrap);
    _manager.run();
  }

  /**
   * Returns the SocketManager for this pastry node.
   *
   * @return The SocketManager for this pastry node.
   */
  public SocketManager getSocketManager() {
    return _manager;
  }

  /**
   * Returns the SocketNodeHandlePool for this pastry node.
   *
   * @return The SocketNodeHandlePool for this pastry node.
   */
  public DistNodeHandlePool getNodeHandlePool() {
    return _pool;
  }

  public void nodeIsReady() {
  }

  /**
   * Method for simulating the death of this node. Should only be used for
   * testing purposes.
   */
  public void kill() {
    _manager.kill();
  }
}
