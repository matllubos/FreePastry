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

package rice.pastry.rmi;

import rice.pastry.*;
import rice.pastry.dist.*;
import rice.pastry.leafset.*;
import rice.pastry.routing.*;
import rice.pastry.messaging.*;
import rice.pastry.join.*;
import rice.pastry.client.*;

import java.util.*;
import java.rmi.server.UnicastRemoteObject;
import java.rmi.*;

/**
 * An RMI-exported Pastry node. Its remote interface is exported over RMI.
 *
 * @version $Id$
 *
 * @author Sitaram Iyer
 */
public class RMIPastryNode extends DistPastryNode implements RMIRemoteNodeI {
  
  // size of the thread pool for asynchronous RMI sends
  // if RMISendHandlerPoolSize == 0, all RMI calls are blocking
  private static final int RMISendHandlerPoolSize = 8;

  // maximal size of the send queue in messages
  private static final int RMISendQueueMaxSize = 256;
  // maximal size of the receive queue in messages
  private static final int RMIRcvQueueMaxSize = 16;

  private RMIRemoteNodeI remotestub;
  private RMINodeHandlePool handlepool;
  private int port;

  private LinkedList rcvQueue;
  private LinkedList sendQueue;

  // a thread class that handles incoming messages
  //
  private class RcvMsgHandler implements Runnable {
    public void run() {
      while (true) {
        Message msg = null;
        synchronized (rcvQueue) {
          while (rcvQueue.size() == 0) {
            try {
              rcvQueue.wait();
            } catch (InterruptedException e) {}
          }

          try {
            msg = (Message) rcvQueue.removeFirst();
          } catch (NoSuchElementException e) {
            System.out.println("no msg despite size = " + rcvQueue.size());
            continue;
          }
        }
        
        /*
         * The sender of this message is alive. So if we have a
         * handle in our pool with this Id, then it should be
         * reactivated.
         */
        NodeId sender = msg.getSenderId();
        if (Log.ifp(6))
          System.out.println("received " +
                             (msg instanceof RouteMessage ? "route" : "direct")
                             + " msg from " + sender + ": " + msg);
        if (sender != null) handlepool.activate(sender);

        receiveMessage(msg);
      }
    }
  }

  // a thread class that handles outgoing messages
  //
  private class SendMsgHandler implements Runnable {
    public void run() {
      Message msg = null;
      RMINodeHandle handle = null;

      while (true) {
        synchronized (sendQueue) {
          while (sendQueue.size() == 0) {
            try {
              sendQueue.wait();
            } catch (InterruptedException e) {}
          }

          try {
            msg = (Message) sendQueue.removeFirst();
            handle = (RMINodeHandle) sendQueue.removeFirst();
          } catch (NoSuchElementException e) {
            System.out.println("no msg despite size = " + rcvQueue.size());
            continue;
          }
        }

        // do the blocking RMI call
        if (msg == null)
          handle.doPing();
        else
          handle.doSend(msg);
      }
    }
  }


  /**
   * Constructor
   */
  public RMIPastryNode(NodeId id) {
    super(id);
    remotestub = null;
    handlepool = null;
    rcvQueue = new LinkedList();
    sendQueue = new LinkedList();
  }

  /**
   * accessor method for elements in RMIPastryNode, called by
   * RMIPastryNodeFactory.
   *
   * @param hp Node handle pool
   * @param p RMIregistry port
   * @param lsmf Leaf set maintenance frequency. 0 means never.
   * @param rsmf Route set maintenance frequency. 0 means never.
   */
  public void setRMIElements(RMINodeHandlePool hp, int p, int lsmf, int rsmf) {
    handlepool = hp;
    port = p;
    leafSetMaintFreq = lsmf;
    routeSetMaintFreq = rsmf;
  }

  /**
   * accessor method for RMI handle pool.
   *
   * @return handle pool
   */
  public DistNodeHandlePool getNodeHandlePool() { return handlepool; }

  /**
   * Returns the given row of the routing table
   *
   * @return The rowth row of the routing table
   */
  public RouteSet[] getRouteRow(int row) {
    return getRoutingTable().getRow(row);
  }

  /**
   * Called after the node is initialized.
   *
   * @param bootstrap The node which this node should boot off of.
   */
  public void doneNode(NodeHandle bootstrap) {
    super.doneNode(bootstrap);

    new Thread(new RcvMsgHandler()).start();
    for (int i=0; i<RMISendHandlerPoolSize; i++)
      new Thread(new SendMsgHandler()).start();

    try {
      remotestub = (RMIRemoteNodeI) UnicastRemoteObject.exportObject(this);
    } catch (RemoteException e) {
      System.out.println("Unable to acquire stub for Pastry node: " + e.toString());
    }

    ((RMINodeHandle)localhandle).setRemoteNode(remotestub);

    initiateJoin(bootstrap);
  }

  /**
   * Called from PastryNode when the join succeeds, whereupon it rebinds
   * the node into the RMI registry. Happens after the registry lookup, so
   * the node never ends up discovering itself.
   */
  public final void nodeIsReady() {
    //System.out.println("RMIPastryNode:nodeIsReady()");
    super.nodeIsReady();

    try {
      Naming.rebind("//:" + port + "/Pastry", remotestub);
    } catch (Exception e) {
      System.out.println("Unable to bind Pastry node in rmiregistry: " + e.toString());
    }
  }

  /**
   * Proxies to the local node to accept a message. For synchronization
   * purposes, it only adds the message to the rcvQueue and signals the
   * message handler thread.
   * @param msg the message
   * @param the expected id of the this node
   */
  public void remoteReceiveMessage(Message msg, NodeId hopDest) throws java.rmi.RemoteException {
    if (!hopDest.equals(getNodeId())) {
      // message arrived here erroneously, probably due to a stale handle
      // throw an exception
      //System.out.println("RMI: wrong receiver" + hopDest + getNodeId());
      throw new java.rmi.RemoteException("RMI: wrong receiver");
    }

    synchronized (rcvQueue) {
      if (msg.getPriority() == 0)
        rcvQueue.addFirst(msg);
      else
        rcvQueue.add(msg);

      if (rcvQueue.size() > RMIRcvQueueMaxSize) {
        msg = (Message) rcvQueue.removeLast();
        System.out.println("RMI: rcv queue at limit, dropping message.." + msg);
      } else
        rcvQueue.notify();
    }
  }

  /**
   * Enqueues a message for asynchronous transmission
   * @param msg the message (if msg == null, do a ping)
   * @param handle the nodeHandle to which the message should be sent
   */
  public void enqueueSendMsg(Message msg, RMINodeHandle handle) {
    if (RMISendHandlerPoolSize == 0) {
      // do a blocking RMI call
      if (msg == null)
        handle.doPing();
      else
        handle.doSend(msg);
    } else {
      // enqueue the message
      int len;

      synchronized (sendQueue) {
        if (msg != null && (msg.getPriority() == 0)) {
          sendQueue.addFirst(handle);
          sendQueue.addFirst(msg);
        } else {
          sendQueue.add(msg);
          sendQueue.add(handle);
        }

        len = sendQueue.size() / 2;
        if (len > RMISendQueueMaxSize) {
          sendQueue.removeLast();
          msg = (Message) sendQueue.removeLast();
          System.out.println("RMI: send queue at limit, dropping message.." + msg);
        } else
          sendQueue.notify();
      }

      if (Log.ifp(8)) System.out.println("RMI: sendQueue len=" + len);
    }
  }

  /**
   * Testing purposes only!
   */
  public void resign() {
    super.resign();

    try {
      UnicastRemoteObject.unexportObject(this, true); // force
    } catch (NoSuchObjectException e) {
      System.out.println("Unable to unbind Pastry node from rmiregistry: " + e.toString());
    }
  }
}

