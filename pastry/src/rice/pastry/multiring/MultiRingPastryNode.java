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

package rice.pastry.multiring;

import java.util.*;

import rice.pastry.*;
import rice.pastry.client.*;
import rice.pastry.messaging.*;
import rice.pastry.routing.*;
import rice.pastry.leafset.*;
import rice.pastry.security.*;

/**
 * Class which represents a pastry node which is in multiple rings.  It internally
 * contains a pastry node for each seperate ring, and has logic for routing messages
 * between the nodes.  Note that the pastry nodes in all of the different rings have
 * the same NodeId.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */ 

public class MultiRingPastryNode extends PastryNode {

  public static RingId GLOBAL_RING_ID = new RingId();
  
  private MultiRingAppl appl;
  
  private PastryNode primaryNode;

  private MultiRingPastryNode parentNode;
  
  private Vector children;
  
  /**
   * Which takes in the first "real" pastry node.  This should only be called from
   * the factory.
   */
  protected MultiRingPastryNode(PastryNode primaryNode) {
    super(primaryNode.getNodeId());
    this.primaryNode = primaryNode;

    children = new Vector();
    this.appl = new MultiRingAppl(this);
  }

  // ----- INTERESTING METHODS -----

  public void receiveMessage(Message msg) {
    if (msg instanceof RouteMessage) {
      RouteMessage rm = (RouteMessage) msg;

      if (rm.getTarget() instanceof RingNodeId) {
        if (! ((RingNodeId) rm.getTarget()).getRingId().equals(appl.getRingId())) {
          MultiRingPastryNode node = getNextHop(((RingNodeId) rm.getTarget()).getRingId());

          if (node != null) {
            System.out.println("Handing message for " + rm.getTarget() + " to other node " + node + " from " + this);
            node.receiveMessage(rm);
          } else {
            System.out.println("Handing message for " + rm.getTarget() + " to appl for routing");
            appl.routeMultiRingMessage(rm);
          }

          return;
        } 
      } 
    }
    
    primaryNode.receiveMessage(msg);
  }

  public void setBootstrap(NodeHandle bootstrap) {
    appl.setBootstrap(bootstrap);
  }

  public void setParentPastryNode(MultiRingPastryNode parent) {
    if ((children.size() == 0) && (parentNode == null)) {
      parentNode = parent;

      if (parent.getMultiRingAppl().getRingId() != null) {
        appl.addRing(parent.getMultiRingAppl().getRingId());
      }
    } else {
      throw new IllegalArgumentException("Cannot set a parent of a node with children or a parent!");
    }
  }

  public void addChildPastryNode(MultiRingPastryNode child) {
    if (parentNode == null) {
      children.addElement(child);

      if (child.getMultiRingAppl().getRingId() != null) {
        broadcastRingId(child.getMultiRingAppl().getRingId());
      }
    } else {
      throw new IllegalArgumentException("Cannot add a child to a node with a parent!");
    }
  }

  public void broadcastRingId(RingId ringId) {
    if (parentNode != null) {
      parentNode.broadcastRingId(ringId);
    } else {
      appl.addRing(ringId);

      if (children != null) {
        for (int i=0; i<children.size(); i++) {
          ((MultiRingPastryNode) children.elementAt(i)).getMultiRingAppl().addRing(ringId);
        }
      }
    }
  }
    

  public MultiRingAppl getMultiRingAppl() {
    return appl;
  }

  protected MultiRingPastryNode getParent() {
    return parentNode;
  }
  
  private MultiRingPastryNode getNextHop(RingId ringId) {
    for (int i=0; i<children.size(); i++) {
      MultiRingPastryNode node = (MultiRingPastryNode) children.elementAt(i);

      if (node.getMultiRingAppl().getRingId().equals(ringId)) {
        return node;
      }
    }

    return parentNode;
  }
  
  // ----- METHODS WHICH JUST PASS THROUGH TO THE PASTRY NODE -----

  public final void setElements(NodeHandle lh, PastrySecurityManager sm, MessageDispatch md, LeafSet ls, RoutingTable rt) {
    primaryNode.setElements(lh, sm, md, ls, rt);
  }

  public NodeHandle getLocalHandle() { return primaryNode.getLocalHandle(); }

  public NodeId getNodeId() { return primaryNode.getNodeId(); }

  public boolean isReady() { return primaryNode.isReady(); }

  public void nodeIsReady() { primaryNode.nodeIsReady(); }

  public void setReady() { primaryNode.setReady(); }

  public boolean isClosest(NodeId key) { return primaryNode.isClosest(key); }

  public LeafSet getLeafSet() { return primaryNode.getLeafSet(); }

  public RoutingTable getRoutingTable() { return primaryNode.getRoutingTable(); }

  public void initiateJoin(NodeHandle bootstrap) { primaryNode.initiateJoin(bootstrap); }

  public void addLeafSetObserver(Observer o) { primaryNode.addLeafSetObserver(o); }

  public void deleteLeafSetObserver(Observer o) { primaryNode.deleteLeafSetObserver(o); }

  public void addRouteSetObserver(Observer o) { primaryNode.addRouteSetObserver(o); }

  public void deleteRouteSetObserver(Observer o) { primaryNode.deleteRouteSetObserver(o); }

  public void registerReceiver(Credentials cred, Address address, MessageReceiver receiver) {
    primaryNode.registerReceiver(cred, address, receiver);
  }

  public void registerApp(PastryAppl app) {
    primaryNode.registerApp(app);
  }

  public ScheduledMessage scheduleMsg(Message msg, long delay) { return primaryNode.scheduleMsg(msg, delay); }

  public ScheduledMessage scheduleMsg(Message msg, long delay, long period) { return primaryNode.scheduleMsg(msg, delay, period); }

  public ScheduledMessage scheduleMsgAtFixedRate(Message msg, long delay, long period) { return primaryNode.scheduleMsgAtFixedRate(msg, delay, period); }
  
  public String toString() {
    return "[MRNode " + primaryNode + "]";
  }
}


