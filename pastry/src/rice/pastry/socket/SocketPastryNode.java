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

- Neither  the name  of Rice  University (RICE) nor the names of its
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

import java.io.*;
import java.net.*;
import java.util.*;

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
  private InetSocketAddress address;
  
  // The SelectorManager, controlling the selector
  private SelectorManager manager;
  
  // The SocketManager, controlling the sockets
  private SocketCollectionManager sManager;
  
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
  
  /**
   * Helper method which allows the WirePastryNodeFactory to
   * initialize a number of the pastry node's elements.
   *
   * @param address The address of this pastry node.
   * @param manager The socket manager for this pastry node.
   * @param lsmf Leaf set maintenance frequency. 0 means never.
   * @param rsmf Route set maintenance frequency. 0 means never.
   */
  public void setSocketElements(InetSocketAddress address,
                                SelectorManager manager,
                                SocketCollectionManager sManager,
                                SocketNodeHandlePool pool,
                                int lsmf,
                                int rsmf) {
    this.address = address;
    this.manager = manager;
    this.sManager = sManager;
    this.pool = pool;
    this.leafSetMaintFreq = lsmf;
    this.routeSetMaintFreq = rsmf;
  }
  
  /**
   * Called after the node is initialized.
   *
   * @param bootstrap The node which this node should boot off of.
   */
  public void doneNode(NodeHandle bootstrap) {
    super.doneNode(bootstrap);
    
    initiateJoin(bootstrap);
    manager.run();
  }
  
  /**
   * Returns the SelectorManager for this pastry node.
   *
   * @return The SelectorManager for this pastry node.
   */
  public SelectorManager getSelectorManager() {
    return manager;
  }
  
  /**
   * Returns the SocketManager for this pastry node.
   *
   * @return The SocketManager for this pastry node.
   */
  public SocketCollectionManager getSocketCollectionManager() {
    return sManager;
  }
  
  /**
   * Returns the WireNodeHandlePool for this pastry node.
   *
   * @return The WireNodeHandlePool for this pastry node.
   */
  public DistNodeHandlePool getNodeHandlePool() {
    return pool;
  }
  
  /**
   * Method for simulating the death of this node. Should only be used for
   * testing purposes.
   */
  public void kill() {
    super.kill();
    manager.kill();
  }
  
  /**
   * Prints out a String representation of this node
   *
   * @return a String
   */
  public String toString() {
    return "SocketNodeHandle (" + getNodeId() + ")\n"; 
  }
}
