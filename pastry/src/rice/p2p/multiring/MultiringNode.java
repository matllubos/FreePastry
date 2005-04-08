
package rice.p2p.multiring;

import java.util.*;
import rice.p2p.commonapi.*;
import rice.p2p.multiring.messaging.*;
import rice.p2p.scribe.*;

/**
 * @(#) MultiringNode.java
 *
 * This class wraps a Node, enabling it to support multiple hierarchitcal
 * scoped rings.  In order to use the mulitring functionality, only one change
 * is necessary:  when one constructs a Node, one should change
 *
 * Node test = factory.newNode(...);
 *
 * to 
 *
 * Node test = new MultiringNode(factory.newNode(...));
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */
public class MultiringNode implements Node, ScribeClient {
  
  /**
   * The node which this mulitring node is wrapping
   */
  protected Node node;
  
  /**
   * The Id which represents the current ring this node is a member of
   */
  protected Id ringId;
  
  /**
   * The Scribe application which the node uses to do routing
   */
  protected Scribe scribe;
  
  /**
   * The collection, which keeps track of the other nodes on the ring node
   */
  protected MultiringNodeCollection collection;
  
  /**
   * The list of all of the endpoints connected to this node
   */
  protected Hashtable endpoints;
  
  /**
   * A cached IdFactory for internal use
   */
  protected MultiringIdFactory factory;
  
  /**
   * Constructor
   *
   * @param node The node which this multiring node is wrapping
   * @param ringId The Id of this node's ring
   */
  public MultiringNode(Id ringId, Node node) {
    this.node = node;
    this.ringId = ringId;
    this.endpoints = new Hashtable();
    this.scribe = new ScribeImpl(this, "Multiring");
    this.collection = new MultiringNodeCollection(this);
    this.factory = (MultiringIdFactory) getIdFactory();
  }
  
  /**
   * Constructor
   *
   * @param node The node which this multiring node is wrapping
   * @param ringId The Id of this node's ring
   * @param existing An existing node which this node should pair with
   */
  public MultiringNode(Id ringId, Node node, MultiringNode existing) {
    this(ringId, node);
    this.collection = existing.getCollection();
    
    this.collection.addNode(this);
  }
  
  public NodeHandle getLocalNodeHandle() {
    return new MultiringNodeHandle(getRingId(), node.getLocalNodeHandle());
  }
  
  /**
   * This returns a VirtualizedNode specific to the given application and
   * instance name to the application, which the application can then use
   * in order to send an receive messages.
   *
   * @param application The Application
   * @param instance An identifier for a given instance
   * @return The endpoint specific to this applicationk, which can be used
   * for message sending/receiving.
   */
  public Endpoint registerApplication(Application application, String instance) {
    Endpoint endpoint = new MultiringEndpoint(this, node.registerApplication(new MultiringApplication(getRingId(), application), application.getClass() + "-" + instance), application);
    endpoints.put(endpoint.getInstance(), endpoint);
    
    return endpoint;
  }
  
  /**
   * This returns a Endpoint specific to the given application and
   * instance name to the application, which the application can then use in
   * order to send an receive messages.  This method allows advanced 
   * developers to specify which "port" on the node they wish their
   * application to register as.  This "port" determines which of the
   * applications on top of the node should receive an incoming 
   * message.
   *
   * NOTE: Use of this method of registering applications is recommended only
   * for advanced users - 99% of all applications should just use the
   * other registerApplication
   * 
   * @param application The Application
   * @param port The port to use
   * @return The endpoint specific to this applicationk, which can be used for
   *         message sending/receiving.
   */
  public Endpoint registerApplication(Application application, int port) {
    Endpoint endpoint = new MultiringEndpoint(this, node.registerApplication(new MultiringApplication(getRingId(), application), port), application);
    endpoints.put(endpoint.getInstance(), endpoint);
  
    return endpoint;
  }
  
  /**
   * Returns the Id of this node
   *
   * @return This node's Id
   */
  public Id getId() {
    return RingId.build(ringId, node.getId());
  }
  
  /**
   * Returns the ringId of this node
   *
   * @return This node's ringId
   */
  public Id getRingId() {
    return ringId;
  }
  
  /**
   * Returns the underlying id of this node
   *
   * @return This node's Id
   */
  public Id getNodeId() {
    return node.getId();
  }
  
  /**
   * Returns this mutliring node's internal node
   *
   * @return The wrapped node
   */
  public Node getNode() {
    return node;
  }
  
  /**
   * Returns the collection this node is a member of
   *
   * @return This node's collection
   */
  public MultiringNodeCollection getCollection() {
    return collection;
  }
  
  /**
   * Returns a factory for Ids specific to this node's protocol.
   *
   * @return A factory for creating Ids.
   */
  public IdFactory getIdFactory() {
    return new MultiringIdFactory(ringId, node.getIdFactory());
  }
  
  /**
   * Method which is used to inform the node that another node has been added to
   * the collection.  This node then joins the appropriate Scribe group to advertise
   * this it can route directly to that node's ring.
   *
   * @param id The Id of the newly added ring
   */
  protected void nodeAdded(Id otherRingId) {
    //System.out.println("JOINING SCRIBE GROUP " + otherRingId + " AT NODE " + getId());
    scribe.subscribe(new Topic(RingId.build(ringId, otherRingId)), this);
  }
  
  /**
   * This method implements the ring routing algortihm attempting to find a suitable
   * nextHop for the message.  First, this method checks to see if there is a local node
   * which can get the message closer to the target.  If there is no such node, this
   * method anycasts the message to the scribe group for the next ring in the heirarchy.
   *
   * @param id The destination Id of the message.
   * @param message The message to deliver
   * @param hint The first node to send this message to, optional
   */
  void route(RingId id, Message message, String application) {    
    if (id.getRingId().equals(ringId)) {
      MultiringEndpoint endpoint = (MultiringEndpoint) endpoints.get(application);
      endpoint.route(id, message, null);
    } else {
      //System.out.println("ANYCASTING TO SCRIBE GROUP " + getTarget(id) + " AT NODE " + getId() + " FOR APPLICATION " + application);
      scribe.anycast(new Topic(RingId.build(ringId, getTarget(id))), new RingMessage(id, message, application));
    }
  }
  
  /**
   * Implements the ring routing mechanism at the local node.  Thus, if the target ringId has
   * our ringId as a prefix, we route to substring(id, length+1).  Otherwise, we route to 
   * substring(getId(), length-1).
   *
   * @param id The target
   * @return The ringId we should route to
   */
  private Id getTarget(RingId id) {
    int shared = collection.getLengthOfSharedPrefix((RingId) getId(), id);
    int thisLength = collection.getLength((RingId) getId());
    int targetLength = collection.getLength(id);
    
    if (shared == thisLength)
      return makeTarget(id, thisLength + 1);
    else
      return makeTarget((RingId) getId(), thisLength - 1);
  }
  
  /**
   * Utility method for building a target id of specified length - all remaining bytes
   * are zeroed out
   *
   * @param id The target id
   * @param length The length in bytes
   * @return The target
   */
  private Id makeTarget(RingId id, int length) {
    byte[] current = id.getRingId().toByteArray();
    byte[] bytes = new byte[current.length];
    
    for (int j=0; j<collection.BASE * length; j++) 
      bytes[bytes.length - 1 - j] = current[bytes.length - 1 - j];
    
    return factory.buildNormalId(bytes);
  }
  
  /**
   * This method is invoked when an anycast is received for a topic
   * which this client is interested in.  The client should return
   * whether or not the anycast should continue.
   *
   * @param topic The topic the message was anycasted to
   * @param content The content which was anycasted
   * @return Whether or not the anycast should continue
   */
  public boolean anycast(Topic topic, ScribeContent content) {
    if (content instanceof RingMessage) {
      RingMessage rm = (RingMessage) content;
      //System.out.println("RECEIVED ANYCAST TO " + rm.getId() + " AT NODE " + getId());
      collection.route(rm.getId(), rm.getMessage(), rm.getApplication());
    } else {
      System.out.println("Received unrecognized message " + content);
    }
    
    return true;
  }
  
  /**
   * This method is invoked when a message is delivered for a topic this
   * client is interested in.
   *
   * @param topic The topic the message was published to
   * @param content The content which was published
   */
  public void deliver(Topic topic, ScribeContent content) {
    System.out.println("Received unexpected delivery on topic " + topic + " of " + content);
  }
  
  /**
   * Informs this client that a child was added to a topic in
   * which it was interested in.
   *
   * @param topic The topic to unsubscribe from
   * @param child The child that was added
   */
  public void childAdded(Topic topic, NodeHandle child) {
  }
  
  /**
   * Informs this client that a child was removed from a topic in
   * which it was interested in.
   *
   * @param topic The topic to unsubscribe from
   * @param child The child that was removed
   */
  public void childRemoved(Topic topic, NodeHandle child) {
  }
  
  /**
   * Informs the client that a subscribe on the given topic failed
   * - the client should retry the subscribe or take appropriate
   * action.
   *
   * @param topic The topic which the subscribe failed on
   */
  public void subscribeFailed(Topic topic) {
    System.out.println(getId() + ": Received error joining ringId topic " + topic + " - trying again.");
    nodeAdded(((RingId) topic.getId()).getId());
  }
  
  /**
   * Prints out the string
   *
   * @return A string
   */
  public String toString() {
    return "{MultiringNode " + getId() + "}";
  }
}




