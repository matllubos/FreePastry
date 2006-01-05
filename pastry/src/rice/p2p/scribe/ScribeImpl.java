
package rice.p2p.scribe;

import java.util.*;
import java.util.prefs.*;

import rice.*;
import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.environment.params.Parameters;
import rice.p2p.commonapi.*;
import rice.p2p.scribe.messaging.*;

/**
 * @(#) ScribeImpl.java Thie provided implementation of Scribe.
 *
 * @version $Id$
 * @author Alan Mislove
 */
public class ScribeImpl implements Scribe, Application {
  
  /**
   * The interval with which to perform maintenance
   */
  public final int MAINTENANCE_INTERVAL;

  /**
   * the timeout for a subscribe message
   */
  public final int MESSAGE_TIMEOUT;

  /**
   * the hashtable of topic -> TopicManager
   */
  public Hashtable topics;

  /**
   * this scribe's policy
   */
  protected ScribePolicy policy;

  /**
   * this application's endpoint
   */
  protected Endpoint endpoint;

  /**
   * the local node handle
   */
  protected NodeHandle handle;

  /**
   * the hashtable of outstanding messages
   */
  private Hashtable outstanding;
  
  /**
   * The hashtable of outstanding lost messags
   */
  private Hashtable lost;

  /**
   * the next unique id
   */
  private int id;
  
  Environment environment;

  Logger logger;
  
  private String instance;
  
  /**
   * Constructor for Scribe, using the default policy.
   *
   * @param node The node below this Scribe implementation
   * @param instance The unique instance name of this Scribe
   */
  public ScribeImpl(Node node, String instance) {
    this(node, new ScribePolicy.DefaultScribePolicy(node.getEnvironment()), instance);
  }

  /**
   * Constructor for Scribe
   *
   * @param node The node below this Scribe implementation
   * @param policy The policy for this Scribe
   * @param instance The unique instance name of this Scribe
   */
  public ScribeImpl(Node node, ScribePolicy policy, String instance) {
    this.environment = node.getEnvironment();
    logger = environment.getLogManager().getLogger(ScribeImpl.class, instance);
    
    Parameters p = environment.getParameters();
    MAINTENANCE_INTERVAL = p.getInt("p2p_scribe_maintenance_interval");
    MESSAGE_TIMEOUT = p.getInt("p2p_scribe_message_timeout");
    this.instance = instance;
    this.endpoint = node.registerApplication(this, instance);
    this.topics = new Hashtable();
    this.outstanding = new Hashtable();
    this.lost = new Hashtable();
    this.policy = policy;
    this.handle = endpoint.getLocalNodeHandle();
    this.id = Integer.MIN_VALUE;
    
    // schedule the period liveness checks of the parent
    endpoint.scheduleMessage(new MaintenanceMessage(), environment.getRandomSource().nextInt(MAINTENANCE_INTERVAL), MAINTENANCE_INTERVAL);

    if (logger.level <= Logger.FINER) logger.log(endpoint.getId() + ": Starting up Scribe");
  }

  public Environment getEnvironment() {
    return environment; 
  }
  
  /**
   * Returns the current policy for this scribe object
   *
   * @return The current policy for this scribe
   */
  public ScribePolicy getPolicy() {
    return policy;
  }

  /**
   * Sets the current policy for this scribe object
   *
   * @param policy The current policy for this scribe
   */
  public void setPolicy(ScribePolicy policy) {
    this.policy = policy;
  }

  /**
   * Returns the Id of the local node
   *
   * @return The Id of the local node
   */
  public Id getId() {
    return endpoint.getId();
  }

  /**
   * Returns the list of clients for a given topic
   *
   * @param topic The topic to return the clients of
   * @return The clients of the topic
   */
  public ScribeClient[] getClients(Topic topic) {
    if (topics.get(topic) != null) {
      return ((TopicManager) topics.get(topic)).getClients();
    }

    return new ScribeClient[0];
  }

  /**
   * Returns the list of children for a given topic
   *
   * @param topic The topic to return the children of
   * @return The children of the topic
   */
  public NodeHandle[] getChildren(Topic topic) {
    if (topics.get(topic) != null) {
      return ((TopicManager) topics.get(topic)).getChildren();
    }

    return new NodeHandle[0];
  }

  /**
   * Returns the parent for a given topic
   *
   * @param topic The topic to return the parent of
   * @return The parent of the topic
   */
  public NodeHandle getParent(Topic topic) {
    if (topics.get(topic) != null) {
      return ((TopicManager) topics.get(topic)).getParent();
    }

    return null;
  }

  /**
   * Returns whether or not this Scribe is the root for the given topic
   *
   * @param topic The topic in question
   * @return Whether or not we are currently the root
   */
  public boolean isRoot(Topic topic) {
    NodeHandleSet set = endpoint.replicaSet(topic.getId(), 1);

    if (set.size() == 0)
      return false;
    else
      return set.getHandle(0).getId().equals(endpoint.getId());
  }

  /**
   * Internal method for sending a subscribe message
   *
   * @param Topic topic
   */
  private void sendSubscribe(Topic topic, ScribeClient client, ScribeContent content) {
    sendSubscribe(topic, client, content, null);
  }

  /**
   * Internal method for sending a subscribe message
   *
   * @param Topic topic
   */
  private void sendSubscribe(Topic topic, ScribeClient client, ScribeContent content, Id previousParent) {
    id++;

    if (logger.level <= Logger.FINEST) logger.log(endpoint.getId() + ": Sending subscribe message for topic " + topic + " client:"+client);
    //logException(Logger.FINEST,"Stack Trace",new Exception("StackTrace"));
    
    // TODO: This should go to the ScribeImpl
    if (client == null) {
      ScribeClient[] clients = getClients(topic); 
      if (clients.length > 0)
        client = clients[0];
    }
    
    if (client != null)
      outstanding.put(new Integer(id), client);

    endpoint.route(topic.getId(), new SubscribeMessage(handle, topic, previousParent, id, content), null);
    CancellableTask task = endpoint.scheduleMessage(new SubscribeLostMessage(handle, topic, id), MESSAGE_TIMEOUT);
    
    lost.put(new Integer(id), task);
  }

  /**
   * Internal method which processes an ack message
   *
   * @param message The ackMessage
   */
  private void ackMessageReceived(SubscribeAckMessage message) {
    ScribeClient client = (ScribeClient) outstanding.remove(new Integer(message.getId()));
    if (logger.level <= Logger.FINER) logger.log(endpoint.getId() + ": Removing client " + client + " from list of outstanding for ack " + message.getId());
    CancellableTask task = (CancellableTask) lost.remove(new Integer(message.getId()));
    
    if (task != null)
      task.cancel();
  }

  /**
   * Internal method which processes a subscribe failed message
   *
   * @param message THe lost message
   */
  private void failedMessageReceived(SubscribeFailedMessage message) {
    ScribeClient client = (ScribeClient) outstanding.remove(new Integer(message.getId()));
    lost.remove(new Integer(message.getId()));
    
    if (logger.level <= Logger.FINER) logger.log(endpoint.getId() + ": Telling client " + client + " about FAILURE for outstanding ack " + message.getId());
    
    if (client != null)
      client.subscribeFailed(message.getTopic());
  }

  /**
   * Internal method which processes a subscribe lost message
   *
   * @param message THe lost message
   */
  private void lostMessageReceived(SubscribeLostMessage message) {
    ScribeClient client = (ScribeClient) outstanding.remove(new Integer(message.getId()));
    lost.remove(new Integer(message.getId()));

    if (logger.level <= Logger.FINER) logger.log(endpoint.getId() + ": Telling client " + client + " about LOSS for outstanding ack " + message.getId());
    
    if (client != null)
      client.subscribeFailed(message.getTopic());
  }

  // ----- SCRIBE METHODS -----

  /**
   * Subscribes the given client to the provided topic. Any message published to the topic will be
   * delivered to the Client via the deliver() method.
   *
   * @param topic The topic to subscribe to
   * @param client The client to give messages to
   */
  public void subscribe(Topic topic, ScribeClient client) {
    subscribe(topic, client, null);
  }

  /**
   * Subscribes the given client to the provided topic. Any message published to the topic will be
   * delivered to the Client via the deliver() method.
   *
   * @param topic The topic to subscribe to
   * @param client The client to give messages to
   */
  public void subscribe(Topic topic, ScribeClient client, ScribeContent content) {
    if (logger.level <= Logger.FINER) logger.log(endpoint.getId() + ": Subscribing client " + client + " to topic " + topic);

    // if we don't know about this topic, subscribe
    // otherwise, we simply add the client to the list
    if (topics.get(topic) == null) {
      topics.put(topic, new TopicManager(topic, client));

      sendSubscribe(topic, client, content);
    } else {
      TopicManager manager = (TopicManager) topics.get(topic);
      manager.addClient(client);

      if ((manager.getParent() == null) && (! isRoot(topic))) {
        sendSubscribe(topic, client, content);
      }
    }
  }

  /**
   * Unsubscribes the given client from the provided topic.
   *
   * @param topic The topic to unsubscribe from
   * @param client The client to unsubscribe
   */
  public void unsubscribe(Topic topic, ScribeClient client) {
    if (logger.level <= Logger.FINER) logger.log(endpoint.getId() + ": Unsubscribing client " + client + " from topic " + topic);

    if (topics.get(topic) != null) {
      TopicManager manager = (TopicManager) topics.get(topic);

      // if this is the last client and there are no children,
      // then we unsubscribe from the topic
      if (manager.removeClient(client)) {
        topics.remove(topic);
        NodeHandle parent = manager.getParent();

        if (parent != null) {
          endpoint.route(null, new UnsubscribeMessage(handle, topic), parent);
        }
      }
    } else {
      if (logger.level <= Logger.WARNING) logger.log(endpoint.getId() + ": Attempt to unsubscribe client " + client + " from unknown topic " + topic);
    }
  }

  /**
   * Publishes the given message to the topic.
   *
   * @param topic The topic to publish to
   * @param content The content to publish
   */
  public void publish(Topic topic, ScribeContent content) {
    if (logger.level <= Logger.FINER) logger.log(endpoint.getId() + ": Publishing content " + content + " to topic " + topic);

    endpoint.route(topic.getId(), new PublishRequestMessage(handle, topic, content), null);
  }

  /**
   * Anycasts the given content to a member of the given topic
   *
   * @param topic The topic to anycast to
   * @param content The content to anycast
   */
  public void anycast(Topic topic, ScribeContent content) {
    if (logger.level <= Logger.FINER) logger.log(endpoint.getId() + ": Anycasting content " + content + " to topic " + topic);

    endpoint.route(topic.getId(), new AnycastMessage(handle, topic, content), null);
  }

  /**
   * Adds a child to the given topic
   *
   * @param topic The topic to add the child to
   * @param child The child to add
   */
  public void addChild(Topic topic, NodeHandle child) {
    addChild(topic, child, Integer.MAX_VALUE);
  }
   
  /**
   * Adds a child to the given topic, using the specified sequence number in the ack message
   * sent to the child.
   *
   * @param topic The topic
   * @param child THe child to add
   * @param id THe seuqnce number
   */
  protected void addChild(Topic topic, NodeHandle child, int id) {
    if (logger.level <= Logger.FINER) logger.log(endpoint.getId() + ": Adding child " + child + " to topic " + topic);
    TopicManager manager = (TopicManager) topics.get(topic);

    // if we don't know about the topic, we subscribe, otherwise,
    // we simply add the child to the list
    if (manager == null) {
      manager = new TopicManager(topic, child);
      topics.put(topic, manager);

      if (logger.level <= Logger.FINER) logger.log(endpoint.getId() + ": Implicitly subscribing to topic " + topic);
      sendSubscribe(topic, null, null);
    } else {
      manager.addChild(child);
    }

    // we send a confirmation back to the child
    endpoint.route(null, new SubscribeAckMessage(handle, topic, manager.getPathToRoot(), id), child);

    // and lastly notify the policy and all of the clients
    policy.childAdded(topic, child);
    
    ScribeClient[] clients = manager.getClients();

    for (int i = 0; i < clients.length; i++) {
      clients[i].childAdded(topic, child);
    }
  }

  /**
   * Removes a child from the given topic
   *
   * @param topic The topic to remove the child from
   * @param child The child to remove
   */
  public void removeChild(Topic topic, NodeHandle child) {
    removeChild(topic, child, true);
  }

  /**
   * Removes a child from the given topic
   *
   * @param topic The topic to remove the child from
   * @param child The child to remove
   * @param sendDrop Whether or not to send a drop message to the chil
   */
  protected void removeChild(Topic topic, NodeHandle child, boolean sendDrop) {
    if (logger.level <= Logger.FINE) logger.log(endpoint.getId() + ": Removing child " + child + " from topic " + topic);

    if (topics.get(topic) != null) {
      TopicManager manager = (TopicManager) topics.get(topic);

      // if this is the last child and there are no clients, then
      // we unsubscribe, if we are not the root
      if (manager.removeChild(child)) {
        topics.remove(topic);
        NodeHandle parent = manager.getParent();
        if (logger.level <= Logger.FINE) logger.log(endpoint.getId() + ": We no longer need topic " + topic + " - unsubscribing from parent " + parent);

        if (parent != null) {
          endpoint.route(null, new UnsubscribeMessage(handle, topic), parent);
        }
      }

      if ((sendDrop) && (child.isAlive())) {
        if (logger.level <= Logger.FINE) logger.log(endpoint.getId() + ": Informing child " + child + " that he has been dropped from topic " + topic);
        
        // now, we tell the child that he has been dropped
        endpoint.route(null, new DropMessage(handle, topic), child);
      }

      // and lastly notify the policy and all of the clients
      policy.childRemoved(topic, child);
      
      ScribeClient[] clients = manager.getClients();

      for (int i = 0; i < clients.length; i++) {
        clients[i].childRemoved(topic, child);
      }
    } else {
      if (logger.level <= Logger.WARNING) logger.log(endpoint.getId() + ": Unexpected attempt to remove child " + child + " from unknown topic " + topic);
    }
  }
  
  /**
   * Returns the list of topics the given client is subscribed
   * to.
   *
   * @param client The client in question
   * @return The list of topics
   */
  public Topic[] getTopics(ScribeClient client) {
    Vector result = new Vector();
    
    Enumeration e = topics.keys();
    
    while (e.hasMoreElements()) {
      Topic topic = (Topic) e.nextElement();
      if (((TopicManager) topics.get(topic)).containsClient(client))
        result.add(topic);
    }
    
    return (Topic[]) result.toArray(new Topic[0]);
  }


  // ----- COMMON API METHODS -----

  /**
   * This method is invoked on applications when the underlying node is about to forward the given
   * message with the provided target to the specified next hop. Applications can change the
   * contents of the message, specify a different nextHop (through re-routing), or completely
   * terminate the message.
   *
   * @param message The message being sent, containing an internal message along with a destination
   *      key and nodeHandle next hop.
   * @return Whether or not to forward the message further
   */
  public boolean forward(final RouteMessage message) {
    if (logger.level <= Logger.FINEST) logger.log(endpoint.getId() + ": Forward called with " + message.getMessage());
    
    if (message.getMessage() instanceof AnycastMessage) {
      AnycastMessage aMessage = (AnycastMessage) message.getMessage();

      // get the topic manager associated with this topic
      TopicManager manager = (TopicManager) topics.get(aMessage.getTopic());

      // if it's a subscribe message, we must handle it differently
      if (message.getMessage() instanceof SubscribeMessage) {
        SubscribeMessage sMessage = (SubscribeMessage) message.getMessage();

        // if this is our own subscribe message, ignore it
        if (sMessage.getSource().getId().equals(endpoint.getId())) {
          return true;
        }

        if (manager != null) {
          // first, we have to make sure that we don't create a loop, which would occur
          // if the subcribing node's previous parent is on our path to the root
          Id previousParent = sMessage.getPreviousParent();
          List path = Arrays.asList(manager.getPathToRoot());

          if (path.contains(previousParent)) {
            if (logger.level <= Logger.INFO) logger.log(endpoint.getId() + ": Rejecting subscribe message from " +
                      sMessage.getSubscriber() + " for topic " + sMessage.getTopic() +
                      " because we are on the subscriber's path to the root.");
            return true;
          }
        }
          
        ScribeClient[] clients = new ScribeClient[0];
        NodeHandle[] handles = new NodeHandle[0];

        if (manager != null) {
          clients = manager.getClients();
          handles = manager.getChildren();
        }

        // check if child is already there
        if (Arrays.asList(handles).contains(sMessage.getSubscriber())){
          return false;
        }

        // see if the policy will allow us to take on this child
        if (policy.allowSubscribe(sMessage, clients, handles)) {
          if (logger.level <= Logger.FINER) logger.log(endpoint.getId() + ": Hijacking subscribe message from " +
            sMessage.getSubscriber() + " for topic " + sMessage.getTopic());

          // if so, add the child
          addChild(sMessage.getTopic(), sMessage.getSubscriber(), sMessage.getId());
          return false;
        }

        // otherwise, we are effectively rejecting the child
        if (logger.level <= Logger.FINER) logger.log(endpoint.getId() + ": Rejecting subscribe message from " +
          sMessage.getSubscriber() + " for topic " + sMessage.getTopic());

        // if we are not associated with this topic at all, we simply let the subscribe go
        // closer to the root
        if (manager == null) {
          return true;
        }
      } else {
        // if we are not associated with this topic at all, let the
        // anycast continue
        if (manager == null) {
          return true;
        }

        ScribeClient[] clients = manager.getClients();

        // see if one of our clients will accept the anycast
        for (int i = 0; i < clients.length; i++) {
          if (clients[i].anycast(aMessage.getTopic(), aMessage.getContent())) {
            if (logger.level <= Logger.FINER) logger.log(endpoint.getId() + ": Accepting anycast message from " +
              aMessage.getSource() + " for topic " + aMessage.getTopic());

            return false;
          }
        }

        // if we are the orginator for this anycast and it already has a destination,
        // we let it go ahead
        if (aMessage.getSource().getId().equals(endpoint.getId()) &&
            ( message.getNextHopHandle() != null) &&
            (! handle.equals(message.getNextHopHandle()))) {
          return true;
        }

        if (logger.level <= Logger.FINER) logger.log(endpoint.getId() + ": Rejecting anycast message from " +
          aMessage.getSource() + " for topic " + aMessage.getTopic());
      }

      // add the local node to the visited list
      aMessage.addVisited(endpoint.getLocalNodeHandle());

      // allow the policy to select the order in which the nodes are visited
      policy.directAnycast(aMessage, manager.getParent(), manager.getChildren());

      // reset the source of the message to be us
      aMessage.setSource(endpoint.getLocalNodeHandle());

      // get the next hop
      NodeHandle handle = aMessage.getNext();

      // make sure that the next node is alive
      while ((handle != null) && (!handle.isAlive())) {
        handle = aMessage.getNext();
      }

      if (logger.level <= Logger.FINER) logger.log(endpoint.getId() + ": Forwarding anycast message for topic " + aMessage.getTopic() + "on to " + handle);

      if (handle == null) {
        if (logger.level <= Logger.FINE) logger.log(endpoint.getId() + ": Anycast " + aMessage + " failed.");

        // if it's a subscribe message, send a subscribe failed message back
        // as a courtesy
        if (aMessage instanceof SubscribeMessage) {
          SubscribeMessage sMessage = (SubscribeMessage) aMessage;
          if (logger.level <= Logger.FINER) logger.log(endpoint.getId() + ": Sending SubscribeFailedMessage to " + sMessage.getSubscriber());

          endpoint.route(null,
                         new SubscribeFailedMessage(handle, sMessage.getTopic(), sMessage.getId()),
                         sMessage.getSubscriber());
        }
      } else {
        endpoint.route(null, aMessage, handle);
      }

      return false;
    }

    return true;
  }

  /**
   * This method is called on the application at the destination node for the given id.
   *
   * @param id The destination id of the message
   * @param message The message being sent
   */
  public void deliver(Id id, Message message) {
    if (logger.level <= Logger.FINEST) logger.log(endpoint.getId() + ": Deliver called with " + id + " " + message);
    
    if (message instanceof AnycastMessage) {
      AnycastMessage aMessage = (AnycastMessage) message;

      // if we are the recipient to someone else's subscribe, then we should have processed
      // this message in the forward() method.
      // Otherwise, we received our own subscribe message, which means that we are
      // the root
      if (aMessage.getSource().getId().equals(endpoint.getId())) {
        if (aMessage instanceof SubscribeMessage) {
          SubscribeMessage sMessage = (SubscribeMessage) message;

          outstanding.remove(new Integer(sMessage.getId()));
          if (logger.level <= Logger.FINE) logger.log(endpoint.getId() + ": Received our own subscribe message " + aMessage + " for topic " +
            aMessage.getTopic() + " - we are the root.");
        } else {
          if (logger.level <= Logger.WARNING) logger.log(endpoint.getId() + ": Received unexpected delivered anycast message " + aMessage + " for topic " +
            aMessage.getTopic() + " - was generated by us.");
        }
      } else {
        // here, we have had a subscribe message delivered, which means that we are the root, but
        // our policy says that we cannot take on this child
        if (aMessage instanceof SubscribeMessage) {
          SubscribeMessage sMessage = (SubscribeMessage) aMessage;
          if (logger.level <= Logger.FINE) logger.log(endpoint.getId() + ": Sending SubscribeFailedMessage (at root) to " + sMessage.getSubscriber());

          endpoint.route(null,
                         new SubscribeFailedMessage(handle, sMessage.getTopic(), sMessage.getId()),
                         sMessage.getSubscriber());
        } else {
          if (logger.level <= Logger.WARNING) logger.log(endpoint.getId() + ": Received unexpected delivered anycast message " + aMessage + " for topic " +
                      aMessage.getTopic() + " - not generated by us, but was expected to be.");
        }
      }
    } else if (message instanceof SubscribeAckMessage) {
      SubscribeAckMessage saMessage = (SubscribeAckMessage) message;
      TopicManager manager = (TopicManager) topics.get(saMessage.getTopic());

      ackMessageReceived(saMessage);

      if (logger.level <= Logger.FINE) logger.log(endpoint.getId() + ": Received subscribe ack message from " + saMessage.getSource() + " for topic " + saMessage.getTopic());

      if (! saMessage.getSource().isAlive()) {
        if (logger.level <= Logger.WARNING) logger.log(endpoint.getId() + ": Received subscribe ack message from " + saMessage.getSource() + " for topic " + saMessage.getTopic());
      }
      
      // if we're the root, reject the ack message
      if (isRoot(saMessage.getTopic())) {
        if (logger.level <= Logger.FINE) logger.log(endpoint.getId() + ": Received unexpected subscribe ack message (we are the root) from " +
                 saMessage.getSource() + " for topic " + saMessage.getTopic());
        endpoint.route(null, new UnsubscribeMessage(handle, saMessage.getTopic()), saMessage.getSource());
      } else {
        // if we don't know about this topic, then we unsubscribe
        // if we already have a parent, then this is either an errorous
        // subscribe ack, or our path to the root has changed.
        if (manager != null) {
          if (manager.getParent() == null) {
            manager.setParent(saMessage.getSource());
          }

          if (manager.getParent().equals(saMessage.getSource())) {
            manager.setPathToRoot(saMessage.getPathToRoot());
          } else {
            if (logger.level <= Logger.WARNING) logger.log(endpoint.getId() + ": Received somewhat unexpected subscribe ack message (already have parent " + manager.getParent() +
                        ") from " + saMessage.getSource() + " for topic " + saMessage.getTopic() + " - the new policy is now to accept the message");
  
            
            NodeHandle parent = manager.getParent();
            manager.setParent(saMessage.getSource());
            manager.setPathToRoot(saMessage.getPathToRoot());

            endpoint.route(null, new UnsubscribeMessage(handle, saMessage.getTopic()), parent);
          }
        } else {
          if (logger.level <= Logger.WARNING) logger.log(endpoint.getId() + ": Received unexpected subscribe ack message from " +
                      saMessage.getSource() + " for unknown topic " + saMessage.getTopic());
              
          endpoint.route(null, new UnsubscribeMessage(handle, saMessage.getTopic()), saMessage.getSource());
        }
      }
    } else if (message instanceof SubscribeLostMessage) {
      SubscribeLostMessage slMessage = (SubscribeLostMessage) message;
      
      lostMessageReceived(slMessage);
    } else if (message instanceof SubscribeFailedMessage) {
      SubscribeFailedMessage sfMessage = (SubscribeFailedMessage) message;

      failedMessageReceived(sfMessage);
    } else if (message instanceof PublishRequestMessage) {
      PublishRequestMessage prMessage = (PublishRequestMessage) message;
      TopicManager manager = (TopicManager) topics.get(prMessage.getTopic());

      if (logger.level <= Logger.FINER) logger.log(endpoint.getId() + ": Received publish request message with data " +
        prMessage.getContent() + " for topic " + prMessage.getTopic());

      // if message is for a non-existant topic, drop it on the floor (we are the root, after all)
      // otherwise, turn it into a publish message, and forward it on
      if (manager == null) {
        if (logger.level <= Logger.FINE) logger.log(endpoint.getId() + ": Received publish request message for non-existent topic " +
          prMessage.getTopic() + " - dropping on floor.");
      } else {
        deliver(prMessage.getTopic().getId(), new PublishMessage(prMessage.getSource(), prMessage.getTopic(), prMessage.getContent()));
      }
    } else if (message instanceof PublishMessage) {
      PublishMessage pMessage = (PublishMessage) message;
      TopicManager manager = (TopicManager) topics.get(pMessage.getTopic());

      if (logger.level <= Logger.FINER) logger.log(endpoint.getId() + ": Received publish message with data " + pMessage.getContent() + " for topic " + pMessage.getTopic());

      // if we don't know about this topic, or this message did
      // not come from our parent, send an unsubscribe message
      // otherwise, we deliver the message to all clients and forward the
      // message to all children
      if ((manager != null) && ((manager.getParent() == null) || (manager.getParent().equals(pMessage.getSource())))) {
        pMessage.setSource(handle);

        ScribeClient[] clients = manager.getClients();

        for (int i = 0; i < clients.length; i++) {
          if (logger.level <= Logger.FINER) logger.log(endpoint.getId() + ": Delivering publish message with data " + pMessage.getContent() + " for topic " +
            pMessage.getTopic() + " to client " + clients[i]);
          clients[i].deliver(pMessage.getTopic(), pMessage.getContent());
        }

        NodeHandle[] handles = manager.getChildren();

        for (int i = 0; i < handles.length; i++) {
          if (logger.level <= Logger.FINER) logger.log(endpoint.getId() + ": Forwarding publish message with data " + pMessage.getContent() + " for topic " +
            pMessage.getTopic() + " to child " + handles[i]);
          endpoint.route(null, new PublishMessage(endpoint.getLocalNodeHandle(), pMessage.getTopic(), pMessage.getContent()), handles[i]);
        }
      } else {
        if (logger.level <= Logger.WARNING) logger.log(endpoint.getId() + ": Received unexpected publish message from " +
          pMessage.getSource() + " for unknown topic " + pMessage.getTopic());

        endpoint.route(null, new UnsubscribeMessage(handle, pMessage.getTopic()), pMessage.getSource());
      }
    } else if (message instanceof UnsubscribeMessage) {
      UnsubscribeMessage uMessage = (UnsubscribeMessage) message;
      if (logger.level <= Logger.FINE) logger.log(endpoint.getId() + ": Received unsubscribe message from " +
        uMessage.getSource() + " for topic " + uMessage.getTopic());

      removeChild(uMessage.getTopic(), uMessage.getSource(), false);
    } else if (message instanceof DropMessage) {
      DropMessage dMessage = (DropMessage) message;
      if (logger.level <= Logger.FINE) logger.log(endpoint.getId() + ": Received drop message from " + dMessage.getSource() + " for topic " + dMessage.getTopic());
      
      TopicManager manager = (TopicManager) topics.get(dMessage.getTopic());

      if (manager != null) {
        if ((manager.getParent() != null) && manager.getParent().equals(dMessage.getSource())) {
          // we set the parent to be null, and then send out another subscribe message
          manager.setParent(null);
          ScribeClient[] clients = manager.getClients();

          if (clients.length > 0)
            sendSubscribe(dMessage.getTopic(), clients[0], null);
          else
            sendSubscribe(dMessage.getTopic(), null, null);
        } else {
          if (logger.level <= Logger.WARNING) logger.log(endpoint.getId() + ": Received unexpected drop message from non-parent " +
                      dMessage.getSource() + " for topic " + dMessage.getTopic() + " - ignoring");
        }
      } else {
        if (logger.level <= Logger.WARNING) logger.log(endpoint.getId() + ": Received unexpected drop message from " +
                    dMessage.getSource() + " for unknown topic " + dMessage.getTopic() + " - ignoring");
      }
    } else if (message instanceof MaintenanceMessage) {
      if (logger.level <= Logger.FINE) logger.log(endpoint.getId() + ": Received maintenance message");

      Iterator i = topics.values().iterator();
      
      // for each topic, make sure our parent is still alive
      while (i.hasNext()) {
        TopicManager manager = (TopicManager) i.next();
        NodeHandle parent = manager.getParent();
        
        // also send a reverse heartbeat message, which should make sure we are still subscribed
        if (parent != null) {
          endpoint.route(manager.getTopic().getId(), new SubscribeMessage(handle, manager.getTopic(), handle.getId(), -1, null), parent);
          parent.checkLiveness();
        }
      }
    } else {
      if (logger.level <= Logger.WARNING) logger.log(endpoint.getId() + ": Received unknown message " + message + " - dropping on floor.");
    }
  }

  /**
   * This method is invoked to inform the application that the given node has either joined or left
   * the neighbor set of the local node, as the set would be returned by the neighborSet call.
   *
   * @param handle The handle that has joined/left
   * @param joined Whether the node has joined or left
   */
  public void update(NodeHandle handle, boolean joined) {
    Set set = topics.keySet();
    Iterator e;
    synchronized(set) {
      e = new ArrayList(set).iterator();
    }
    TopicManager manager;
    Topic topic;

    while (e.hasNext()) {
      topic = (Topic) e.next();
      manager = (TopicManager) topics.get(topic);

      if (joined) {
        // check if new guy is root, we were old root, then subscribe
        if (manager.getParent() == null){
          // send subscribe message
          sendSubscribe(topic, null, null);
        }
      } else {
        if (isRoot(topic) && (manager.getParent() != null)) {
          endpoint.route(null, new UnsubscribeMessage(handle, topic), manager.getParent());
          manager.setParent(null);
        }
      }
    }    
  }

  /**
   * Class which keeps track of a given topic
   *
   * @version $Id$
   * @author amislove
   */
  public class TopicManager implements Observer, Destructable {

    /**
     * DESCRIBE THE FIELD
     */
    protected Topic topic;

    /**
     * The current path to the root for this node
     */
    protected Id[] pathToRoot;

    /**
     * DESCRIBE THE FIELD
     */
    protected Vector clients;

    /**
     * DESCRIBE THE FIELD
     */
    protected Vector children;

    /**
     * DESCRIBE THE FIELD
     */
    protected NodeHandle parent;

    /**
     * Constructor for TopicManager.
     *
     * @param topic DESCRIBE THE PARAMETER
     * @param client DESCRIBE THE PARAMETER
     */
    public TopicManager(Topic topic, ScribeClient client) {
      this(topic);

      addClient(client);
    }

    /**
     * Constructor for TopicManager.
     *
     * @param topic DESCRIBE THE PARAMETER
     * @param child DESCRIBE THE PARAMETER
     */
    public TopicManager(Topic topic, NodeHandle child) {
      this(topic);

      addChild(child);
    }

    /**
     * Constructor for TopicManager.
     *
     * @param topic DESCRIBE THE PARAMETER
     */
    protected TopicManager(Topic topic) {
      this.topic = topic;
      this.clients = new Vector();
      this.children = new Vector();

      setPathToRoot(new Id[0]);
    }
    
    /**
     * Gets the topic of the TopicManager object
     *
     * @return The Parent value
     */
    public Topic getTopic() {
      return topic;
    }

    /**
     * Gets the Parent attribute of the TopicManager object
     *
     * @return The Parent value
     */
    public NodeHandle getParent() {
      return parent;
    }

    /**
     * Gets the Clients attribute of the TopicManager object
     *
     * @return The Clients value
     */
    public ScribeClient[] getClients() {
      return (ScribeClient[]) clients.toArray(new ScribeClient[0]);
    }
    
    /**
     * Returns whether or not this topic manager contains the given
     * client.
     *
     * @param client The client in question
     * @return Whether or not this manager contains the client
     */
    public boolean containsClient(ScribeClient client) {
      return clients.contains(client);
    }

    /**
     * Gets the Children attribute of the TopicManager object
     *
     * @return The Children value
     */
    public NodeHandle[] getChildren() {
      return (NodeHandle[]) children.toArray(new NodeHandle[0]);
    }

    /**
     * Gets the PathToRoot attribute of the TopicManager object
     *
     * @return The PathToRoot value
     */
    public Id[] getPathToRoot() {
      return pathToRoot;
    }

    /**
     * Sets the PathToRoot attribute of the TopicManager object
     *
     * @param pathToRoot The new PathToRoot value
     */
    public void setPathToRoot(Id[] pathToRoot) {
      // build the path to the root for the new node
      this.pathToRoot = new Id[pathToRoot.length + 1];
      System.arraycopy(pathToRoot, 0, this.pathToRoot, 0, pathToRoot.length);
      this.pathToRoot[pathToRoot.length] = endpoint.getId();
      
      // now send the information out to our children
      NodeHandle[] children = getChildren();
      for (int i=0; i<children.length; i++) {
        if (Arrays.asList(this.pathToRoot).contains(children[i].getId())) {
          endpoint.route(null, new DropMessage(handle, topic), children[i]);
          removeChild(children[i]);
        } else {
          endpoint.route(null, new SubscribeAckMessage(handle, topic, getPathToRoot(), Integer.MAX_VALUE), children[i]);
        }
      }
    }

    /**
     * Sets the Parent attribute of the TopicManager object
     *
     * @param handle The new Parent value
     */
    public void setParent(NodeHandle handle) {
      if ((handle != null) && (parent != null)) {
        if (logger.level <= Logger.WARNING) logger.log(endpoint.getId() + ": Unexpectedly changing parents for topic " + topic);
      }
      
      if (parent != null) {
        parent.deleteObserver(this);
      }
      
      parent = handle;
      setPathToRoot(new Id[0]);
      
      if ((parent != null) && parent.isAlive()) {
        parent.addObserver(this);
      }
    }

    /**
     * DESCRIBE THE METHOD
     *
     * @param o DESCRIBE THE PARAMETER
     * @param arg DESCRIBE THE PARAMETER
     */
    public void update(Observable o, Object arg) {
      if (arg.equals(NodeHandle.DECLARED_DEAD)) {
        if (children.contains(o)) {
          if (logger.level <= Logger.FINE) logger.log(endpoint.getId() + ": Child " + o + " for topic " + topic + " has died - removing.");

          ScribeImpl.this.removeChild(topic, (NodeHandle) o);
        } 
        
        if (o.equals(parent)) {
          // if our parent has died, then we must resubscribe to the topic
          if (logger.level <= Logger.FINE) logger.log(endpoint.getId() + ": Parent " + parent + " for topic " + topic + " has died - resubscribing.");
          
          setParent(null);

          if (clients.size() > 0)
            sendSubscribe(topic, (ScribeClient) clients.elementAt(0), null, ((NodeHandle) o).getId());
          else
            sendSubscribe(topic, null, null, ((NodeHandle) o).getId());
        } else {
          if (logger.level <= Logger.WARNING) logger.log(endpoint.getId() + ": Received unexpected update from " + o);
          o.deleteObserver(this);
        }
      }
    }

    /**
     * Adds a feature to the Client attribute of the TopicManager object
     *
     * @param client The feature to be added to the Client attribute
     */
    public void addClient(ScribeClient client) {
      if (!clients.contains(client)) {
        clients.add(client);
      }
    }

    /**
     * DESCRIBE THE METHOD
     *
     * @param client DESCRIBE THE PARAMETER
     * @return DESCRIBE THE RETURN VALUE
     */
    public boolean removeClient(ScribeClient client) {
      clients.remove(client);

      boolean unsub = ((clients.size() == 0) && (children.size() == 0));

      // if we're going to unsubscribe, then we remove ourself as
      // as observer
      if (unsub && (parent != null)) {
        parent.deleteObserver(this);
      }

      return unsub;
    }

    /**
     * Adds a feature to the Child attribute of the TopicManager object
     *
     * @param child The feature to be added to the Child attribute
     */
    public void addChild(NodeHandle child) {
      if ((!children.contains(child)) && child.isAlive()) {
        children.add(child);
        child.addObserver(this);
      }
    }

    /**
     * DESCRIBE THE METHOD
     *
     * @param child DESCRIBE THE PARAMETER
     * @return DESCRIBE THE RETURN VALUE
     */
    public boolean removeChild(NodeHandle child) {
      children.remove(child);
      child.deleteObserver(this);

      boolean unsub = ((clients.size() == 0) && (children.size() == 0));

      // if we're going to unsubscribe, then we remove ourself as
      // as observer
      if (unsub && (parent != null)) {
        parent.deleteObserver(this);
      }

      return unsub;
    }

    public void destroy() {
      if (parent!=null) 
        parent.deleteObserver(this);
      Iterator i = children.iterator();
      while(i.hasNext()) {
        NodeHandle child = (NodeHandle)i.next();
        child.deleteObserver(this);
      }
    }
  }

  public void destroy() {
    Iterator topicIter = topics.values().iterator();
    while(topicIter.hasNext()) {
      TopicManager topicManager = (TopicManager)topicIter.next(); 
      topicManager.destroy();
    }
  }
}
