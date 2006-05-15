package rice.pastry.testing;

import rice.pastry.*;
import rice.pastry.client.*;
import rice.pastry.routing.*;
import rice.pastry.messaging.*;

import java.util.*;

/**
 * A very simple ping object.
 * 
 * @version $Id$
 * 
 * @author Andrew Ladd
 */

public class PingClient extends PastryAppl {
  private static class PingAddress {
    private static int myCode = 0x9219d8ff;

    public static int getCode() {
      return myCode;
    }
  }

  private static int pingAddress = PingAddress.getCode();

  public PingClient(PastryNode pn) {
    super(pn);
  }

  public int getAddress() {
    return pingAddress;
  }

  public void sendPing(Id nid) {
    // routeMessage, sans the getAddress() in the RouteMessage constructor
    routeMsg(nid, new PingMessage(pingAddress, getNodeId(), nid), 
        new SendOptions());
  }

  public void sendTrace(Id nid) {
    System.out.println("sending a trace from " + getNodeId() + " to " + nid);
    // sendEnrouteMessage
    routeMsg(nid, new PingMessage(pingAddress, getNodeId(), nid), 
        new SendOptions());
  }

  public void messageForAppl(Message msg) {
    System.out.print(msg);
    System.out.println(" received");
  }

  public boolean enrouteMessage(Message msg, Id from, NodeHandle nextHop,
      SendOptions opt) {
    System.out.print(msg);
    System.out.println(" at " + getNodeId());

    return true;
  }

  public void leafSetChange(NodeHandle nh, boolean wasAdded) {
    if (true) return;
    System.out.println("at... " + getNodeId() + "'s leaf set");
    System.out.print("node " + nh.getNodeId() + " was ");
    if (wasAdded)
      System.out.println("added");
    else
      System.out.println("removed");
  }

  public void routeSetChange(NodeHandle nh, boolean wasAdded) {
    if (true) return;
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
  private Id source;

  private Id target;

  public PingMessage(int pingAddress, Id src, Id tgt) {
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

