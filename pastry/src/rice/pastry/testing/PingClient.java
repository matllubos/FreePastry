package rice.pastry.testing;

import rice.pastry.*;
import rice.pastry.client.*;
import rice.pastry.routing.*;
import rice.pastry.messaging.*;
import rice.pastry.security.*;

import java.util.*;

/**
 * A very simple ping object.
 * 
 * @version $Id$
 * 
 * @author Andrew Ladd
 */

public class PingClient extends PastryAppl {
  private static class PingAddress implements Address {
    private int myCode = 0x9219d8ff;

    public int hashCode() {
      return myCode;
    }

    public boolean equals(Object obj) {
      return (obj instanceof PingAddress);
    }

    public String toString() {
      return "[PingAddress]";
    }
  }

  private static Address pingAddress = new PingAddress();

  private Credentials pingCred = new PermissiveCredentials();

  public PingClient(PastryNode pn) {
    super(pn);
  }

  public Address getAddress() {
    return pingAddress;
  }

  public Credentials getCredentials() {
    return pingCred;
  }

  public void sendPing(NodeId nid) {
    // routeMessage, sans the getAddress() in the RouteMessage constructor
    routeMsg(nid, new PingMessage(pingAddress, getNodeId(), nid), pingCred,
        new SendOptions());
  }

  public void sendTrace(NodeId nid) {
    System.out.println("sending a trace from " + getNodeId() + " to " + nid);
    // sendEnrouteMessage
    routeMsg(nid, new PingMessage(pingAddress, getNodeId(), nid), pingCred,
        new SendOptions());
  }

  public void messageForAppl(Message msg) {
    System.out.print(msg);
    System.out.println(" received");
  }

  public boolean enrouteMessage(Message msg, Id from, NodeId nextHop,
      SendOptions opt) {
    System.out.print(msg);
    System.out.println(" at " + getNodeId());

    return true;
  }

  public void leafSetChange(NodeHandle nh, boolean wasAdded) {
    System.out.println("at... " + getNodeId() + "'s leaf set");
    System.out.print("node " + nh.getNodeId() + " was ");
    if (wasAdded)
      System.out.println("added");
    else
      System.out.println("removed");
  }

  public void routeSetChange(NodeHandle nh, boolean wasAdded) {
    System.out.println("at... " + getNodeId() + "'s route set");
    System.out.print("node " + nh.getNodeId() + " was ");
    if (wasAdded)
      System.out.println("added");
    else
      System.out.println("removed");
  }
}

/**
 * DO NOT declare this inside PingClient; see HelloWorldApp for details.
 */

class PingMessage extends Message {
  private NodeId source;

  private NodeId target;

  public PingMessage(Address pingAddress, NodeId src, NodeId tgt) {
    super(pingAddress);
    source = src;
    target = tgt;
  }

  public String toString() {
    String s = "";
    s += "ping from " + source + " to " + target;
    return s;
  }
}

