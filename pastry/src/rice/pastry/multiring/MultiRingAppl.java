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

import java.io.*;
import java.util.*;

import rice.pastry.multiring.messaging.*;

import rice.pastry.*;
import rice.pastry.client.*;
import rice.pastry.dist.*;
import rice.pastry.messaging.*;
import rice.pastry.routing.*;
import rice.pastry.leafset.*;
import rice.pastry.security.*;
import rice.pastry.standard.*;

import rice.scribe.*;
import rice.scribe.messaging.*;

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

public class MultiRingAppl extends PastryAppl implements IScribeApp {

  public static int REMINDER_TIMEOUT = 5000;
  
  private RingId ringId;

  private Scribe scribe;

  private PendingMessages pending;

  private RouteCache cache;
  
  /**
   * The credentials of this appl.
   */
  private Credentials credentials = new PermissiveCredentials();
  
  /**
   * Constructor
   */
  protected MultiRingAppl(MultiRingPastryNode node) {
    super(node);

    scribe = new Scribe(node, credentials);
    scribe.registerApp(this);

    pending = new PendingMessages();
    cache = new RouteCache();
  }

  public RingId getRingId() {
    return ringId;
  }

  public void addRing(RingId ringId) {
    System.out.println("Joining SCRIBE group " + ringId.getRingId() + " at " + thePastryNode.getNodeId() + " (" + this.ringId + ")");
    scribe.join(ringId.getRingId(), this, credentials, null);
  }

  public void messageForAppl(Message msg) {
    if (msg instanceof RingIdRequestMessage) {
      RingIdRequestMessage request = (RingIdRequestMessage) msg;
      routeMsgDirect(request.getSource(), new RingIdResponseMessage(ringId), credentials, null);
    } else if (msg instanceof RingIdResponseMessage) {
      RingIdResponseMessage response = (RingIdResponseMessage) msg;

      if (ringId == null) {
        ringId = response.getRingId();

        System.out.println("Received ring ID: " + ringId);
        ((MultiRingPastryNode) thePastryNode).broadcastRingId(ringId);
      }	else {
        System.out.println("Received unexpected ringid response (" + response.getRingId() + ") - ignoring.");
      }
    } else if (msg instanceof RingLookupResponseMessage) {
      RingLookupResponseMessage response = (RingLookupResponseMessage) msg;
      RouteMessage[] messages = pending.get(response.getRingId());

      cache.put(response.getRingId(), response.getSource());

      System.out.println("Received lookup response to " + response.getRingId() + " - sending all queued messages.");
      
      for (int i=0; i<messages.length; i++) {
        forwardMessage(response.getSource(), messages[i]);
      }
    } else if (msg instanceof RingForwardMessage) {
      RingForwardMessage forward = (RingForwardMessage) msg;
      System.out.println("Received forward message " + forward + " - passing to pastry node.");

      thePastryNode.receiveMessage(forward.getMessage());
    } else if (msg instanceof RingLookupReminderMessage) {
      RingLookupReminderMessage reminder = (RingLookupReminderMessage) msg;

      RouteMessage[] messages = pending.get(reminder.getRingId());

      System.out.println("Could not find direct route to " + reminder.getRingId());

      // NEED TO ROUTE TO GLOBAL RING HERE...
    } else {
      System.out.println("Received unknown message " + msg + " - ignoring.");
    } 
  }
  
  public void setBootstrap(NodeHandle bootstrap) {
    if (bootstrap == null) {
      if (((MultiRingPastryNode) thePastryNode).getParent() != null) {
        ringId = new RingId((new RandomNodeIdFactory()).generateNodeId());

        System.out.println("Generated new random ring ID: " + ringId);

        ((MultiRingPastryNode) thePastryNode).broadcastRingId(ringId);
      } else {
        ringId = MultiRingPastryNode.GLOBAL_RING_ID;
        
        System.out.println("Used global ringId: " + ringId);
      }
    } else {
      System.out.println("Sending a messge to " + bootstrap.getNodeId() + " to determine ring id");
      routeMsgDirect(bootstrap, new RingIdRequestMessage(getNodeHandle()), credentials, null);
    }
  }

  protected void routeMultiRingMessage(RouteMessage rm) {
    System.out.println("Received request to route message to " + rm.getTarget());
    NodeHandle handle = cache.get((RingId) rm.getTarget());

    if (handle == null) {
      System.out.println("No cached handle to " + rm.getTarget() + " is available - enqueueing.");
      boolean send = pending.add(rm);

      if (send) {
        System.out.println("Sending lookup message via anycast to ringId " + rm.getTarget());
        RingId ringId = (RingId) rm.getTarget();
        scribe.anycast(ringId.getRingId(), new RingLookupRequestMessage(ringId), credentials);

        thePastryNode.scheduleMsg(new RingLookupReminderMessage(ringId), REMINDER_TIMEOUT);
      }
    } else {
      System.out.println("Found cached handle " + handle + " to " + rm.getTarget() + " forwarding.");

      forwardMessage(handle, rm);
    }
  }

  private void forwardMessage(NodeHandle handle, RouteMessage message) {
    System.out.println("Sending forward request to " + handle + " for ringId " + message.getTarget());
    RingForwardMessage forward = new RingForwardMessage(message);
    routeMsgDirect(handle, forward, credentials, null);
  }

  public boolean anycastHandler(ScribeMessage msg) {
    MessageAnycast anycast = (MessageAnycast) msg;
    System.out.println("Received anycast for ringId " + ringId + " from " + anycast.getSource() + " - responding.");
    RingLookupRequestMessage request = (RingLookupRequestMessage) anycast.getData();
    RingLookupResponseMessage response = new RingLookupResponseMessage(getNodeHandle(), request.getRingId());

    routeMsgDirect(anycast.getSource(), response, credentials, null);

    return false;
  }

  private class PendingMessages {

    private Hashtable table;

    public PendingMessages() {
      table = new Hashtable();
    }

    public boolean add(RouteMessage m) {
      RingId ringId = (RingId) m.getTarget();
      Vector v = (Vector) table.get(ringId.getRingId());
      boolean result = false;
      
      if (v == null) {
        v = new Vector();
        table.put(ringId.getRingId(), v);
        result = true;
      }

      v.addElement(m);

      return result;
    }

    public RouteMessage[] get(RingId ringId) {
      Vector v = (Vector) table.remove(ringId.getRingId());

      if (v == null) {
        return new RouteMessage[0];
      }

      RouteMessage[] result = new RouteMessage[v.size()];

      for (int i=0; i<v.size(); i++) {
        result[i] = (RouteMessage) v.elementAt(i);
      }

      return result;
    }
  }

  private class RouteCache {

    private Hashtable table;

    public RouteCache() {
      table = new Hashtable();
    }

    public void put(RingId ringId, NodeHandle handle) {
      Vector v = (Vector) table.get(ringId.getRingId());

      if (v == null) {
        v = new Vector();
        table.put(ringId.getRingId(), v);
      }

      v.addElement(handle);
    }

    public NodeHandle get(RingId ringId) {
      Vector v = (Vector) table.get(ringId.getRingId());

      if (v == null) {
        return null;
      }

      NodeHandle handle = (NodeHandle) v.elementAt(0);

      if (handle.isAlive()) {
        return handle;
      } else {
        v.remove(handle);

        if (v.size() == 0) {
          table.remove(ringId.getRingId());
        }
        
        return get(ringId);
      }
    }
  }

  // ----- BORING STUFF -----
  
  public Address getAddress() {
    return MultiRingApplAddress.instance();
  }

  public Credentials getCredentials() {
    return credentials;
  }

  public void isNewRoot(NodeId topicId) {};

  public void newParent(NodeId topicId, NodeHandle newParent, Serializable data) {};
  
  public void scribeIsReady() {};

  public void receiveMessage( ScribeMessage msg ) {};

  public void forwardHandler( ScribeMessage msg ) {};

  public void subscribeHandler(NodeId topicId, NodeHandle child, boolean wasAdded, Serializable obj ) {};

  public void faultHandler( ScribeMessage msg, NodeHandle faultyParent ) {};
}


