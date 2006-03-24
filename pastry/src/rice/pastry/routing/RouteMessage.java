package rice.pastry.routing;

import rice.pastry.commonapi.*;

import rice.pastry.*;
import rice.pastry.messaging.*;
import rice.pastry.security.*;

import java.io.*;

/**
 * A route message contains a pastry message that has been wrapped to be sent to
 * another pastry node.
 * 
 * @version $Id$
 * 
 * @author Andrew Ladd
 */

public class RouteMessage extends Message implements Serializable,
    rice.p2p.commonapi.RouteMessage {
  private static final long serialVersionUID = 3492981895989180093L;

  private Id target;

  private Message internalMsg;

  private NodeHandle prevNode;

  private transient SendOptions opts;

  private Address auxAddress;

  public transient NodeHandle nextHop;

  /**
   * Constructor.
   * 
   * @param target this is id of the node the message will be routed to.
   * @param msg the wrapped message.
   * @param cred the credentials for the message.
   */

  public RouteMessage(Id target, Message msg, Credentials cred) {
    super(new RouterAddress());
    this.target = target;
    internalMsg = msg;
    this.opts = new SendOptions();

    nextHop = null;
  }

  /**
   * Constructor.
   * 
   * @param target this is id of the node the message will be routed to.
   * @param msg the wrapped message.
   * @param cred the credentials for the message.
   * @param opts the send options for the message.
   */

  public RouteMessage(Id target, Message msg, Credentials cred, SendOptions opts) {
    super(new RouterAddress());
    this.target = target;
    internalMsg = msg;
    this.opts = opts;

    nextHop = null;
  }

  /**
   * Constructor.
   * 
   * @param dest the node this message will be routed to
   * @param msg the wrapped message.
   * @param cred the credentials for the message.
   * @param opts the send options for the message.
   * @param aux an auxilary address which the message after each hop.
   */

  public RouteMessage(NodeHandle dest, Message msg, Credentials cred,
      SendOptions opts, Address aux) {
    super(new RouterAddress());
    this.target = dest.getNodeId();
    internalMsg = msg;
    this.opts = opts;
    nextHop = dest;
    auxAddress = aux;
  }

  /**
   * Constructor.
   * 
   * @param target this is id of the node the message will be routed to.
   * @param msg the wrapped message.
   * @param cred the credentials for the message.
   * @param aux an auxilary address which the message after each hop.
   */

  public RouteMessage(Id target, Message msg, Credentials cred, Address aux) {
    super(new RouterAddress());
    this.target = target;
    internalMsg = msg;
    this.opts = new SendOptions();

    auxAddress = aux;

    nextHop = null;
  }

  /**
   * Constructor.
   * 
   * @param target this is id of the node the message will be routed to.
   * @param msg the wrapped message.
   * @param cred the credentials for the message.
   * @param opts the send options for the message.
   * @param aux an auxilary address which the message after each hop.
   */

  public RouteMessage(Id target, Message msg, Credentials cred,
      SendOptions opts, Address aux) {
    super(new RouterAddress());
    this.target = target;
    internalMsg = msg;
    this.opts = opts;

    auxAddress = aux;

    nextHop = null;
  }

  /**
   * Constructor.
   * 
   * @param target this is id of the node the message will be routed to.
   * @param msg the wrapped message.
   * @param firstHop the nodeHandle of the first hop destination
   * @param aux an auxilary address which the message after each hop.
   */

  public RouteMessage(Id target, Message msg, NodeHandle firstHop, Address aux) {
    super(new RouterAddress());
    this.target = (Id) target;
    internalMsg = msg;
    this.opts = new SendOptions();
    auxAddress = aux;
    nextHop = firstHop;
  }

  /**
   * Routes the messages if the next hop has been set up.
   * 
   * @param localId the node id of the local node.
   * 
   * @return true if the message got routed, false otherwise.
   */

  public boolean routeMessage(NodeHandle localHandle) {
    if (nextHop == null)
      return false;
    setSender(localHandle);

    NodeHandle handle = nextHop;
    nextHop = null;

    if (localHandle.equals(handle)) {
      localHandle.getLocalNode().send(handle, internalMsg);
    } else
      localHandle.getLocalNode().send(handle, this);

    return true;
  }

  /**
   * Gets the target node id of this message.
   * 
   * @return the target node id.
   */

  public Id getTarget() {
    return target;
  }

  public NodeHandle getPrevNode() {
    return prevNode;
  }

  public void setPrevNode(NodeHandle n) {
    prevNode = n;
  }

  public NodeHandle getNextHop() {
    return nextHop;
  }

  public void setNextHop(NodeHandle nh) {
    nextHop = nh;
  }

  /**
   * Get priority
   * 
   * @return the priority of this message.
   */

  public int getPriority() {
    return internalMsg.getPriority();
  }

  /**
   * Get receiver address.
   * 
   * @return the address.
   */

  public Address getDestination() {
    if (nextHop == null || auxAddress == null)
      return super.getDestination();

    return auxAddress;
  }

  /**
   * The wrapped message.
   * 
   * @return the wrapped message.
   */

  public Message unwrap() {
    return internalMsg;
  }

  /**
   * Get transmission options.
   * 
   * @return the options.
   */

  public SendOptions getOptions() {
    if (opts == null) {
      opts = new SendOptions();
    }
    return opts;
  }

  public String toString() {
    String str = "";
      str += "[ " + internalMsg + " ]";

    return str;
  }

  // Common API Support

  public rice.p2p.commonapi.Id getDestinationId() {
    return getTarget();
  }

  public rice.p2p.commonapi.NodeHandle getNextHopHandle() {
    return nextHop;
  }

  public rice.p2p.commonapi.Message getMessage() {
    return ((PastryEndpointMessage) unwrap()).getMessage();
  }

  public void setDestinationId(rice.p2p.commonapi.Id id) {
    target = (Id) id;
  }

  public void setNextHopHandle(rice.p2p.commonapi.NodeHandle nextHop) {
    this.nextHop = (NodeHandle) nextHop;
  }

  public void setMessage(rice.p2p.commonapi.Message message) {
    ((PastryEndpointMessage) unwrap()).setMessage(message);
  }
}