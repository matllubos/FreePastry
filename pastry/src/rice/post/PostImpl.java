package rice.post;

import java.io.*; 
import java.security.*;
import java.util.*;
import java.util.logging.* ;

import rice.*;
import rice.Continuation.*;

import rice.p2p.commonapi.*;
import rice.p2p.past.*;
import rice.p2p.replication.*;
import rice.p2p.scribe.*;

import rice.persistence.*;

import rice.post.log.*;
import rice.post.messaging.*;
import rice.post.storage.*;
import rice.post.security.*;
import rice.post.security.ca.*;

/**
 * This class is the service layer which allows 
 * Post applications to use Post functionality.
 *
 * @version $Id$
 * @author Alan Mislove
 * @author Ansley Post
 */
public class PostImpl implements Post, Application, ScribeClient, ReplicationClient {

  /**
   * The replication factor to use for replicating delivery messages
   */
  public static final int REPLICATION_FACTOR = 3;
  
  /**
   * The endpoint used for routing messages
   */
  protected Endpoint endpoint;
  
  /**
   * The local PAST service to use for persistent storage.
   */
  protected Past past;
  
  /**
   * The local Scribe service to use for notification.
   */
  protected Scribe scribe;

  /**
   * The local replication manager for replications notifications
   */
  protected Replication replication;
  
  /**
   * The address of the local user.
   */
  protected PostEntityAddress address;
  
  /**
   * The logger which we will use
   */
  protected Logger logger = Logger.getLogger(this.getClass().getName());
  
  // --- CLIENT SUPPORT ---
  
  /**
   * The list of clients currently using POST.
   */
  private Vector clients;
  
  /**
   * Maps PostClientAddress to PostClient.
   */
  private Hashtable clientAddresses;

  /**
   * A list of the notification messages which we've received, to determine
   * if we're receiveing a message twice
   */
  private Vector receivedMessages;

  /**
   * A storage used to store the pending (to be sent) encrypted notification
   * messages
   */
  private Storage memoryStorage;


  // --- GROUP SUPPORT ---

  /**
   * The list of group keys known by this Post
   */
  private HashMap keys;

  
  // --- BUFFERING SUPPORT ---
  
  /**
   * The data structure to hold the buffered packets.
   */
  private Hashtable bufferedData;

  /**
   * The data structure map DRM Id -> user
   */
  private Hashtable reverseMap;

  
  // --- LOGGING SUPPORT ---

  /**
   * The top level POST log, with pointers to the logs for each application.
   */
  private PostLog log;

  
  // --- STORAGE SUPPORT ---

  /**
   * The storage service for storing data in POST.
   */
  private StorageService storage;
  
  
  // --- SECURITY SUPPORT ---
  
  /**
   * The security service for managing all security related tasks.
   * This field should never be made accessible to anything outside o
   * Post, since it provides access to methods which can encrypt and
   * decrypt using the user's key pair!
   */
  private SecurityService security;
  
  /**
   * The user's public key.
   */
  private KeyPair keyPair;
  
  /**
   * The certificate used to authenticate this user's key pair.
   */
  private PostCertificate certificate;
  
  /**
   * The public key of the certificate authority.
   */
  private PublicKey caPublicKey; 
  

  /**
   * Builds a PostImpl to run on the given pastry node,
   * using the provided PAST and Scribe services.
   *
   * @param node The Pastry node to run on.
   * @param past The PAST service running on this Pastry node.
   * @param address The address of the user in the system
   * @param keyPair The KeyPair of this user
   * @param certificate The certificate authenticating this user
   * @param caPublicKey The public key of the certificate authority
   * @param instance The unique instance name of this POST
   * 
   * @throws PostException if the PostLog could not be accessed
   */
  public PostImpl(Node node,
                  Past past,
                  PostEntityAddress address,
                  KeyPair keyPair,
                  PostCertificate certificate,
                  PublicKey caPublicKey,
                  String instance) throws PostException 
  {
    this.endpoint = node.registerApplication(this, instance);
    this.past = past;
    this.address = address;
    this.keyPair = keyPair;
    this.certificate = certificate;
    this.caPublicKey = caPublicKey;
    
    this.scribe = new ScribeImpl(node, instance);
    this.memoryStorage = new MemoryStorage(node.getIdFactory());
    this.replication = new ReplicationImpl(node, this, REPLICATION_FACTOR, instance + "-POST");

    this.security = new SecurityService();
    this.security.loadModule(new CASecurityModule(caPublicKey));
    this.storage = new StorageService(address, past, node.getIdFactory(), keyPair);

    clients = new Vector();
    clientAddresses = new Hashtable();
    bufferedData = new Hashtable();
    keys = new HashMap();
    receivedMessages = new Vector();
    reverseMap = new Hashtable();
    
    //logger.addHandler(new ConsoleHandler());
    //logger.setLevel(Level.FINE);
    //logger.getHandlers()[0].setLevel(Level.FINE);
    
    logger.fine(endpoint.getId() + ": Constructed new Post with user " + address + " and instance " + instance);
  }

  /**
    * @return The PostEntityAddress of the local user.
   */
  public PostEntityAddress getEntityAddress() {
    return address;
  }

  /**
   * @return The CA's public key
   */
  public PublicKey getCAPublicKey() {
    return caPublicKey;
  }
  
  /**
   * The method by which Pastry passes a message up to POST
   *
   * @param message The message which has arrived
   */
  public void deliver(Id id, Message message) {
    logger.finest(endpoint.getId() + ": Received message " + message + " with target " + id);
    if (message instanceof SignedPostMessageWrapper) {
      processSignedPostMessage(((SignedPostMessageWrapper) message).getMessage());
    } else {
      logger.warning(endpoint.getId() + ": Found unknown message " + message + " - dropping on floor.");
    }
  }
  
  /**
   * This method is invoked on applications when the underlying node
   * is about to forward the given message with the provided target to
   * the specified next hop.  Applications can change the contents of
   * the message, specify a different nextHop (through re-routing), or
   * completely terminate the message.
   *
   * @param message The message being sent, containing an internal message
   * along with a destination key and nodeHandle next hop.
   *
   * @return Whether or not to forward the message further
   */
  public boolean forward(RouteMessage message) {
    return true;
  }
  
  /**
   * This method is invoked to inform the application that the given node
   * has either joined or left the neighbor set of the local node, as the set
   * would be returned by the neighborSet call.
   *
   * @param handle The handle that has joined/left
   * @param joined Whether the node has joined or left
   */
  public void update(NodeHandle handle, boolean joined) {
  }

  /**
   * Method by which Scribe delivers a message to this client.
   *
   * @param msg The incoming message.
   */
  public void deliver(Topic topic, ScribeContent content) {
    logger.finest(endpoint.getId() + ": Received scribe content " + content + " for topic " + topic);
    if (content instanceof SignedPostMessageWrapper) {
      processSignedPostMessage(((SignedPostMessageWrapper) content).getMessage());
    } else {
      logger.warning(endpoint.getId() + ": Found unknown Scribe message " + content + " - dropping on floor.");
    }
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
    return false;
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
  }

  /**
   * Internal method for processing incoming SignedPostMessage.  This
   * method performs all verification checks and processes the
   * message appropriately.
   *
   * @param message The incoming message.
   */
  private void processSignedPostMessage(final SignedPostMessage signedMessage) {
    final PostEntityAddress sender = signedMessage.getMessage().getSender();
    logger.finer(endpoint.getId() + ": Processing signed message " + signedMessage + " from sender " + sender);
    
    getPostLog(sender, new ListenerContinuation("Process Signed Post Message") {
      public void receiveResult(Object o) {
        PostLog senderLog = (PostLog) o;

        // look up sender
        if (senderLog == null) {
          logger.warning(endpoint.getId() + ": Found PostMessage from non-existent sender " + sender + " - dropping on floor.");
          return;
        }

        // verify message is signed
        if (! verifySignedPostMessage(signedMessage, senderLog.getPublicKey())) {
          logger.warning(endpoint.getId() + ": Problem encountered verifying PostMessage from " + sender + " - dropping on floor.");
          return;
        }

        PostMessage message = signedMessage.getMessage();

        if (message instanceof DeliveryRequestMessage) {
          processDeliveryRequestMessage((DeliveryRequestMessage) message);
        } else if (message instanceof DeliveryLookupMessage) {
          processDeliveryLookupMessage((DeliveryLookupMessage) message);
        } else if (message instanceof DeliveryLookupResponseMessage) {
          processDeliveryLookupResponseMessage((DeliveryLookupResponseMessage) message);
        } else if (message instanceof PresenceMessage) {
          processPresenceMessage((PresenceMessage) message);
        } else if (message instanceof EncryptedNotificationMessage) {
          processEncryptedNotificationMessage((EncryptedNotificationMessage) message);
        } else if (message instanceof DeliveryMessage) {
          processDeliveryMessage((DeliveryMessage) message);
        } else if (message instanceof ReceiptMessage) {
          processReceiptMessage((ReceiptMessage) message);
        } else if (message instanceof GroupNotificationMessage) {
          processGroupMessage((GroupNotificationMessage) message);
        } else {
          logger.warning(endpoint.getId() + ": Found unknown Postmessage " + message + " - dropping on floor.");
        }
      }
    });
  }
  
  /**
   * This method processs the processing of a delivery
   * request message by subscribing the appriate scribe
   * group.
   *
   * @param message The incoming message.
   */
  private void processDeliveryRequestMessage(DeliveryRequestMessage message){
    logger.fine(endpoint.getId() + ": Received delivery request from : " + message.getSender() + " to: " + message.getDestination());

    synchronized(bufferedData) {
      Vector userQueue = (Vector) bufferedData.get(message.getDestination());

      if (userQueue == null) {
        logger.finer(endpoint.getId() + ": Creating entry for: " + message.getDestination());

        userQueue = new Vector();
        bufferedData.put(message.getDestination(), userQueue);
        scribe.subscribe(new Topic(message.getDestination().getAddress()), this);

        logger.finer(endpoint.getId() + ": Joined Scribe group rooted at " + message.getDestination().getAddress());
      }

      userQueue.addElement(message.getId());
      memoryStorage.store(message.getId(), message, new ListenerContinuation("Storage of ENM"));
      reverseMap.put(message.getId(), message.getDestination());
    }
  }

  /**
   * This method processs a lookup for a DRM
   *
   * @param message The incoming message.
   */
  private void processDeliveryLookupMessage(final DeliveryLookupMessage message){
    logger.fine(endpoint.getId() + ": Received delivery lookup from : " + message.getSender() + " for: " + message.getId());

    memoryStorage.getObject(message.getId(), new ListenerContinuation("Delivery Lookup Request") {
      public void receiveResult(Object o) {
        if (o != null) {
          DeliveryRequestMessage drm = (DeliveryRequestMessage) o;
          DeliveryLookupResponseMessage dlrm = new DeliveryLookupResponseMessage(address, drm);

          endpoint.route(message.getSource().getId(), new PostPastryMessage(signPostMessage(dlrm)), message.getSource());
        }
      }
    });
  }
  
  /**
   * This method processs a lookup response for a DRM
   *
   * @param message The incoming message.
   */
  private void processDeliveryLookupResponseMessage(DeliveryLookupResponseMessage message){
    logger.fine(endpoint.getId() + ": Received delivery lookup response from : " + message.getSender() + " for: " + message.getEncryptedMessage().getId());
    processDeliveryRequestMessage(message.getEncryptedMessage());
  }

  /**
   * This method handles the receipt of a presence message, which
   * involves routing all pending notification messages to the
   * location advertised in the message.
   *
   * @param message The incoming message
   */
  private void processPresenceMessage(final PresenceMessage message) {
    logger.fine(endpoint.getId() + ": Presence message from : " + message.getSender());

    synchronized (bufferedData) {
      Vector userQueue = (Vector) bufferedData.get(message.getSender());

      if (userQueue != null) {
        if (userQueue.size() == 0) {
          logger.warning(endpoint.getId() + ": ERROR - presence message from : " + message.getSender() + " has empty vector.");
        }

        for (int i=0; i<userQueue.size(); i++) {
          final Id id = (Id) userQueue.elementAt(i);

          memoryStorage.getObject(id, new ListenerContinuation("Retrival of stored ENM") {
            public void receiveResult(Object o) {
              if (o != null) {
                DeliveryRequestMessage drm = (DeliveryRequestMessage) o;
                SignedPostMessage spm = drm.getEncryptedMessage();
                DeliveryMessage dm = new DeliveryMessage(address, endpoint.getId(), id, spm);
                endpoint.route(message.getLocation(), new PostPastryMessage(signPostMessage(dm)), null);
              } 
            }
          });
        }
      } else {
        logger.warning(endpoint.getId() + ": ERROR - presence message from : " + message.getSender() + " should not be received here.");
      }
    }
  }

  /**
   * This method processes a delivery message containing a notification message.
   *
   * @param message The incoming message.
   */
  private void processDeliveryMessage(DeliveryMessage message) {
    logger.fine(endpoint.getId() + ": Delivery message from : " + message.getSender());

    // send receipt
    ReceiptMessage rm = new ReceiptMessage(address, message.getId(), message.getEncryptedMessage());
    endpoint.route(message.getLocation(), new PostPastryMessage(signPostMessage(rm)), null);

    // process internal message, if we haven't seen it before
    if (! receivedMessages.contains(message.getId())) {
      logger.finer(endpoint.getId() + ": Haven't seen message " + message.getId() + " before - accepting.");
      receivedMessages.add(message.getId());
      processSignedPostMessage(message.getEncryptedMessage());
    } else {
      logger.finer(endpoint.getId() + ": I've seen message " + message.getId() + " before - ignoring.");
    }
  }
  
  /**
   * This method processes a notification message by passing it up
   * to the appropriate application.
   *
   * @param message The incoming message.
   */
  private void processEncryptedNotificationMessage(EncryptedNotificationMessage message) {
    logger.fine(endpoint.getId() + ": Encrypted notification message from : " + message.getSender());
    NotificationMessage nm = null;

    // decrypt and verify notification message
    try {
      byte[] key = SecurityUtils.decryptAsymmetric(message.getKey(), keyPair.getPrivate());
      nm = (NotificationMessage) SecurityUtils.deserialize(SecurityUtils.decryptSymmetric(message.getData(), key));
    } catch (Exception e) {
      logger.warning(endpoint.getId() + ": Exception occured which decrypting NotificationMessage " + e + " - dropping on floor.");
      return;
    }

    logger.finer(endpoint.getId() + ": Successfully deserialized notification message from : " + nm.getSender());

    if (! (nm.getSender().equals(message.getSender()))) {
      logger.warning(endpoint.getId() + ": Found ENM from " + message.getSender() + " with internal NM from different sender " +
                         nm.getSender() + " - dropping on floor.");
      return;
    }

    logger.finer(endpoint.getId() + ": DEBUG: successfully verified ENM with NM: " + nm);

    // deliver notification messag
    PostClient client = (PostClient) clientAddresses.get(nm.getClientAddress());

    if (client != null) {
      client.notificationReceived(nm);
    } else {
      logger.warning(endpoint.getId() + ": Found notification message for unknown client " + client + " - dropping on floor.");
    }
  }
  
  
  /**
   * This method handles an incoming receipt message by
   * removing the soft state associated with the delivery
   * request.
   *
   * @param message The incoming message.
   */
  private void processReceiptMessage(ReceiptMessage message) {
    logger.fine(endpoint.getId() + ": Received receipt message from : " + message.getSender());

    Id id = message.getId();
    PostEntityAddress sender = message.getSender();

    // remove message
    synchronized (bufferedData) {
      Vector userQueue = (Vector) bufferedData.get(sender);

      if (userQueue != null) {
        boolean success = userQueue.remove(id);

        if (! success) {
          logger.warning(endpoint.getId() + ": ERROR - Received receiptmessage for unknown message " + id);
        }

        if (userQueue.size() == 0) {
          bufferedData.remove(sender);
          scribe.unsubscribe(new Topic(sender.getAddress()), this);
        }
      } else {
        scribe.unsubscribe(new Topic(sender.getAddress()), this);
      }
    }

    reverseMap.remove(id);
    memoryStorage.unstore(id, new ListenerContinuation("Unstore of DRM"));
  }

  /**
   * This method handles an incoming group multicast message.
   *
   * @param message The incoming message.
   */
  private void processGroupMessage(GroupNotificationMessage message) {
    PostGroupAddress destination = (PostGroupAddress) message.getGroup();

    logger.fine(endpoint.getId() + ": Received group message from: " + destination);

    byte[] key = (byte[]) keys.get(destination);

    logger.finer(endpoint.getId() + ": Using group key " + key + " for decryption.");

    try {
      byte[] plainText = null;

      if (key != null) {
        plainText = SecurityUtils.decryptSymmetric(message.getData(), key);
      } else {
        plainText = message.getData();
      }

      NotificationMessage nm = (NotificationMessage) SecurityUtils.deserialize(plainText);

      // deliver notification message
      PostClient client = (PostClient) clientAddresses.get(nm.getClientAddress());

      if (client != null) {
        client.notificationReceived(nm);
      } else {
        logger.warning(endpoint.getId() + ": Found notification message for unknown client " + client + " - dropping on floor.");
      }
    } catch (Exception e) {
      logger.warning(endpoint.getId() + ": Exception occured while decrypting GroupNotificationMessage " + e + " - dropping on floor.");
    } 
  }
  

  /**
   * @return The PostLog belonging to the this entity,
   */
  public void getPostLog(Continuation command) {
    getPostLog(getEntityAddress(), command);
  }
  
  /**
   * @return The PostLog belonging to the given entity, eg. to acquire
   * another user's public key.
   */
  public void getPostLog(final PostEntityAddress entity, Continuation command) {
    if ((entity.equals(getEntityAddress())) && (log != null)) {
      command.receiveResult(log);
      return;
    }

    logger.fine(endpoint.getId() + ": Looking up postlog for : " + entity);

    storage.retrieveSigned(new SignedReference(entity.getAddress()), new StandardContinuation(command) {
      public void receiveResult(Object o) {
        final PostLog log = (PostLog) o;

        if (log == null) {
          logger.info(endpoint.getId() + ": Could not find postlog for: " + entity);

          if (entity.equals(getEntityAddress())) {
            PostImpl.this.log = new PostLog(entity, keyPair.getPublic(), certificate, PostImpl.this, parent);
            return;
          } else {
            logger.warning(endpoint.getId() + ": PostLog lookup for user " + entity + " failed.");
            parent.receiveResult(null);
            return;
          }
        }

        if ((log.getPublicKey() == null) || (log.getEntityAddress() == null)) {
          parent.receiveException(new PostException("Malformed PostLog: " + log.getPublicKey() + " " + log.getEntityAddress()));
          return;
        }

        if (! (log.getEntityAddress().equals(log.getCertificate().getAddress()) &&
               log.getPublicKey().equals(log.getCertificate().getKey()))) {
          parent.receiveException(new PostException("Malformed PostLog: Certificate does not match log owner."));
          return;
        }

        security.verify(log.getCertificate(), new StandardContinuation(parent) {
          public void receiveResult(Object o) {
            if ((new Boolean(true)).equals(o)) {
              storage.verifySigned(log, log.getPublicKey());
              log.setPost(PostImpl.this);

              if (entity.equals(getEntityAddress())) {
                PostImpl.this.log = log;
              }

              logger.fine(endpoint.getId() + ": Successfully retrieved postlog for: " + entity);

              parent.receiveResult(log);
            } else  {
              logger.warning(endpoint.getId() + ": Ceritficate of PostLog could not be verified for entity " + entity);
              parent.receiveException(new PostException("Certificate of PostLog could not verified for entity: " + entity));
            }
          }
        });
      }
    });
  }
  
  /**
   * This method returns the local storage service.
   *
   * @return The storage service.
   */
  public StorageService getStorageService() {
    return storage;
  }	    
  
  /**
   * Registers a client with this Post 
   *
   * @param client The client to add
   */
  public void addClient(PostClient client) {
    if (! clients.contains(client)) {
      clients.add(client);
      clientAddresses.put(client.getAddress(), client);
    }
  }
  
  /**
   * This method announce's our presence via our scribe tree
   */
  public void announcePresence() {
    logger.finer(endpoint.getId() + ": Publishing presence to the group " + address.getAddress());

    PresenceMessage pm = new PresenceMessage(address, endpoint.getId());
    scribe.publish(new Topic(address.getAddress()), new PostScribeMessage(signPostMessage(pm)));
  }

  /**
   * Removes a client from this PostService.  
   *
   * @param client The client to remove
   */
  public void removeClient(PostClient client) {
    clients.remove(client);
    clientAddresses.remove(client.getAddress());
  }

  
  /**
   * Sends a notification message with destination specified by the members
   * of the NotificationMessage.  Destination parameters include a PostEntityAddress
   * which specifies the group or user to which the notification should go, and a
   * PostClientAddress which specifies the user application to which the notification
   * should go.  The NotificationMessage sent is signed by the sender and is then 
   * encrypted with the public key of each recipient.
   *
   * @param message The notification message to be sent.  Destination parameters
   * are encapsulated inside the message object.
   */
  public void sendNotification(final NotificationMessage message) {
    final PostUserAddress destination = (PostUserAddress) message.getDestination();

    logger.fine(endpoint.getId() + ": Sending notification message " + message + " to: " + destination);

    getPostLog(destination, new ListenerContinuation("Send Notification to " + destination) {
      public void receiveResult(Object o) {
        PostLog destinationLog = (PostLog) o;

        if (destinationLog == null) {
          logger.warning(endpoint.getId() + ": Could not send notification message to non-existant user " + destination);
          return;
        }

        logger.finer(endpoint.getId() + ": Received destination log " + destinationLog);

        Id random = storage.getRandomNodeId();

        logger.finer(endpoint.getId() + ": Picked random node: " + random);

        byte[] cipherText = null;

        try {
          byte[] key = SecurityUtils.generateKeySymmetric();
          byte[] keyCipherText = SecurityUtils.encryptAsymmetric(key, destinationLog.getPublicKey());
          cipherText = SecurityUtils.encryptSymmetric(SecurityUtils.serialize(message), key);

          EncryptedNotificationMessage enm = new EncryptedNotificationMessage(address, keyCipherText, cipherText);
          DeliveryRequestMessage drm = new DeliveryRequestMessage(address, destination, signPostMessage(enm), random);

          logger.finer(endpoint.getId() + ": Sending delivery request to : " + random);

          endpoint.route(random, new PostPastryMessage(signPostMessage(drm)), null);
        } catch (Exception e) {
          logger.warning(endpoint.getId() + ": Exception occured which encrypting NotificationMessage " + e + " - aborting.");
        }
      }
    });
  }

  /**
   * Sends a notification message with destination specified by the members
   * of the NotificationMessage.  Destination parameters include a PostEntityAddress
   * which specifies the group or user to which the notification should go, and a
   * PostClientAddress which specifies the user application to which the notification
   * should go.  The NotificationMessage sent is signed by the sender and is then
   * encrypted with the public key of each recipient.
   *
   * In this method, the notification message is sent directly to the provided node handle,
   * instead of through a group of random nodes via the Scribe tree.
   *
   * @param message The notification message to be sent.  Destination parameters
   * are encapsulated inside the message object.
   */
  public void sendNotificationDirect(final NodeHandle handle, final NotificationMessage message) {
    final PostUserAddress destination = (PostUserAddress) message.getDestination();

    logger.fine(endpoint.getId() + ": Sending notification message " + message + " directly to " + destination + " via " + handle);

    getPostLog(destination, new ListenerContinuation("Send Notification Direct to " + destination + " via " + handle) {
      public void receiveResult(Object o) {
        PostLog destinationLog = (PostLog) o;

        if (destinationLog == null) {
          logger.warning(endpoint.getId() + ": Could not send notification message to non-existant user " + destination);
          return;
        }

        logger.finer(endpoint.getId() + ": Received destination log " + destinationLog);

        Id random = storage.getRandomNodeId();

        logger.finer(endpoint.getId() + ": Picked random node: " + random);

        byte[] cipherText = null;

        try {
          byte[] key = SecurityUtils.generateKeySymmetric();
          byte[] keyCipherText = SecurityUtils.encryptAsymmetric(key, destinationLog.getPublicKey());
          cipherText = SecurityUtils.encryptSymmetric(SecurityUtils.serialize(message), key);
          EncryptedNotificationMessage enm = new EncryptedNotificationMessage(address, keyCipherText, cipherText);

          logger.finer(endpoint.getId() + ": Sending notification message directly to : " + handle);

          endpoint.route(handle.getId(), new PostPastryMessage(signPostMessage(enm)), handle);
        } catch (Exception e) {
          logger.warning(endpoint.getId() + ": Exception occured which encrypting NotificationMessage " + e + " - dropping on floor.");
        } 
      }
    });
  }

  /**
   * This method causes the local POST service to subscribe to the specified address, and
   * use the specified shared key in order to decrypt messages.  If the key is null, then
   * messages are assumed to be unencrypted.  Incoming messages, once verified, will be
   * passed up to the appropriate applciation through the notificationReceived() method.
   *
   * @param address The address to join
   * @param key The shared key to use (or null, if unencrypted)
   */
  public void joinGroup(PostGroupAddress address, byte[] key) {
    keys.put(address, key);

    scribe.subscribe(new Topic(address.getAddress()), this);
  }

  /**
    * This method multicasts the provided notification message to the destination
   * group.  However, this local node *MUST* have already joined this
   * group (through the joinGroup method) in order for this to work properly.
   *
   * @param message The message to send
   */
  public void sendGroup(NotificationMessage message) {
    PostGroupAddress destination = (PostGroupAddress) message.getDestination();
    byte[] key = (byte[]) keys.get(destination);

    logger.fine(endpoint.getId() + ": Sending message " + message + " to group " + destination + " using key " + key);
    
    try {
      byte[] cipherText = null;

      if (key != null) {
        cipherText = SecurityUtils.encryptSymmetric(SecurityUtils.serialize(message), key);
      } else {
        cipherText = SecurityUtils.serialize(message);
      }

      GroupNotificationMessage gnm = new GroupNotificationMessage(address, destination, cipherText);

      logger.finer(endpoint.getId() + ": Built encrypted notfn msg " + gnm + " for destination " + destination);

      scribe.publish(new Topic(destination.getAddress()), new PostScribeMessage(signPostMessage(gnm)));
    } catch (Exception e) {
      logger.warning(endpoint.getId() + ": Exception occured while encrypting GroupNotificationMessage " + e + " - dropping on floor.");
    } 
  }  

  /**
   * Internal utility method for preparing a PostMessage for transmission.  This
   * method signs the message and tells the message that it is about to be sent
   * across the wire.  NOTE: The message should NOT be changes after calling this
   * method, and this method should ALWAYS be called before transmitting a PostMessage.
   *
   * @param message The message to prepare.
   */
  private SignedPostMessage signPostMessage(PostMessage message) {
    try {
      byte[] sig = SecurityUtils.sign(SecurityUtils.serialize(message), keyPair.getPrivate());

      return new SignedPostMessage(message, sig);
    } catch (SecurityException e) {
      logger.warning(endpoint.getId() + ": SecurityException " + e + " occured while siging PostMessage " + message + " - aborting.");
      return null;
    } catch (IOException e) {
      logger.warning(endpoint.getId() + ": IOException " + e + " occured while siging PostMessage " + message + " - aborting.");
      return null;
    } 
  }

  /**
   * This method verifies that a given post message was signed by the user
   * corresponding the to given public key.
   *
   * @param message The message to verify.
   * @param key The key to verify against.
   */
  private boolean verifySignedPostMessage(SignedPostMessage message, PublicKey key) {
    try {
      if (key == null) {
        logger.warning(endpoint.getId() + ": Cannot verify PostMessage with null key!" + message + " " + key);
        return false;
      }
      
      byte[] plainText = SecurityUtils.serialize(message.getMessage());
      byte[] sig = message.getSignature();

      return SecurityUtils.verify(plainText, sig, key);
    } catch (SecurityException e) {
      logger.warning(endpoint.getId() + ": SecurityException " + e + " occured while verifiying PostMessage " + message + " - aborting.");
      return false;
    } catch (IOException e) {
      logger.warning(endpoint.getId() + ": IOException " + e + " occured while verifiying PostMessage " + message + " - aborting.");
      return false;
    }
  }

  /**
   * This upcall is invoked to notify the application that is should
   * fetch the cooresponding keys in this set, since the node is now
   * responsible for these keys also.
   * @param keySet set containing the keys that needs to be fetched
   */
  public void fetch(IdSet keySet) {
    logger.finer(endpoint.getId() + ": Was told to fetch the keyset " + keySet);

    if (replication != null) {
      Iterator i = keySet.getIterator();

      while (i.hasNext()) {
        Id id = (Id) i.next();
        NodeHandleSet set = endpoint.replicaSet(id, REPLICATION_FACTOR);
        DeliveryLookupMessage dlm = new DeliveryLookupMessage(address, endpoint.getLocalNodeHandle(), id);
        logger.finer(endpoint.getId() + ": Sending delivery lookup message for id " + id);
        
        for(int j=0; j<set.size(); j++) {
          endpoint.route(set.getHandle(j).getId(), new PostPastryMessage(signPostMessage(dlm)), set.getHandle(j));
        }
      }
    }
  }

  /**
   * This upcall is to notify the application of the range of keys for
   * which it is responsible. The application might choose to react to
   * call by calling a scan(complement of this range) to the persistance
   * manager and get the keys for which it is not responsible and
   * call delete on the persistance manager for those objects.
   * @param range the range of keys for which the local node is currently
   *              responsible
   */
  public void setRange(IdRange range) {
    IdRange notRange = range.getComplementRange();
    
    logger.fine(endpoint.getId() + ": Removing all pending deliveries in the range " + notRange);

    Continuation c = new Continuation() {
      public void receiveResult(Object o) {
        Iterator i = ((IdSet) o).getIterator();
        Vector v = new Vector();
        while (i.hasNext()) v.add(i.next());
        Iterator notIds = v.iterator();

        while (notIds.hasNext()) {
          Id id = (Id) notIds.next();
          logger.finer(endpoint.getId() + ": Removing deliver request with id " + id);
          processReceiptMessage(new ReceiptMessage((PostEntityAddress) reverseMap.get(id), id, null));
        }
      }

      public void receiveException(Exception e) {
        logger.warning(endpoint.getId() + ": Exception " + e + " occured during removal of objects.");
      }
    };

    memoryStorage.scan(notRange, c);
  }

  /**
   * This upcall should return the set of keys that the application
   * currently stores in this range. Should return a empty IdSet (not null),
   * in the case that no keys belong to this range.
   * @param range the requested range
   */
  public IdSet scan(IdRange range) {
    return memoryStorage.scan(range);
  }
  
  
  public String toString() {
    return "PostImpl[" + address + "]";
  }
}
