package rice.post;
 
import java.security.*;
import java.util.*;
import java.io.*;

import rice.*;
import rice.Continuation.*;
import rice.pastry.security.*;
import rice.pastry.client.*;
import rice.pastry.messaging.*;
import rice.pastry.routing.*;
import rice.pastry.standard.*;
import rice.pastry.*;
import rice.p2p.past.*;
import rice.scribe.*;
import rice.scribe.messaging.*;
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
public class PostImpl extends PastryAppl implements Post, IScribeApp  {
  
  /**
   * The local PAST service to use for persistent storage.
   */
  private Past past;
  
  /**
   * The local Scribe service to use for notification.
   */
  private IScribe scribeService;
  
  /**
   * The address of the local user.
   */
  private PostEntityAddress address;
  
  
  // --- PASTRY SUPPORT ---

  /**
   * The credentials of POST.
   */
  private Credentials credentials = new PermissiveCredentials();
  
  
  // --- CLIENT SUPPORT ---
  
  /**
   * The list of clients currently using POST.
   */
  private Vector clients;
  
  /**
   * Maps PostClientAddress to PostClient.
   */
  private Hashtable clientAddresses;


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

  private RandomNodeIdFactory factory;
  
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
   * @param scribe The Scribe service running on this Pastry node.
   * @param address The address of the user in the system
   * @param keyPair The KeyPair of this user
   * @param certificate The certificate authenticating this user
   * @param caPublicKey The public key of the certificate authority
   * @param instance The unique instance name of this POST
   * 
   * @throws PostException if the PostLog could not be accessed
   */
  public PostImpl(PastryNode node,
                  Past past,
                  IScribe scribe,
                  PostEntityAddress address,
                  KeyPair keyPair,
                  PostCertificate certificate,
                  PublicKey caPublicKey,
                  String instance)
    throws PostException
  {
    super(node, instance);
    
    this.past = past;
    this.scribeService = scribe;
    this.address = address;
    this.keyPair = keyPair;
    this.certificate = certificate;
    this.caPublicKey = caPublicKey;

    security = new SecurityService();
    security.loadModule(new CASecurityModule(caPublicKey));
    storage = new StorageService(address, past, credentials, keyPair);
    factory = new RandomNodeIdFactory();

    clients = new Vector();
    clientAddresses = new Hashtable();
    bufferedData = new Hashtable();
    keys = new HashMap();
  }

  /**
   * @return The PastryAddress of the POST application.
   */
  public Address getAddress() {
    return PostAddress.instance();
  }

  /**
    * @return The PostEntityAddress of the local user.
   */
  public PostEntityAddress getEntityAddress() {
    return address;
  }
  
  /**
   * @return The credentials of POST
   */
   public Credentials getCredentials() {
     return credentials;
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
  public void messageForAppl(Message message) {
    if (message instanceof SignedPostMessageWrapper) {
      processSignedPostMessage(((SignedPostMessageWrapper) message).getMessage());
    } else {
      System.out.println("Found unknown pastry message " + message + " - dropping on floor.");
    }
  }

  /**
   * Method by which Scribe delivers a message to this client.
   *
   * @param msg The incoming message.
   */
  public void receiveMessage(ScribeMessage msg) {
    if (msg.getData() instanceof SignedPostMessageWrapper) {
      processSignedPostMessage(((SignedPostMessageWrapper) msg.getData()).getMessage());
    } else {
      System.out.println("Found unknown Scribe message " + msg.getData() + " - dropping on floor.");
    }
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
    debug("Processing signed message " + signedMessage + " from sender " + sender);
    
    getPostLog(sender, new ListenerContinuation("Process Signed Post Message") {
      public void receiveResult(Object o) {
        PostLog senderLog = (PostLog) o;

        // look up sender
        if (senderLog == null) {
          System.out.println("Found PostMessage from non-existent sender " + sender + " - dropping on floor.");
          return;
        }

        // verify message is signed
        if (! verifySignedPostMessage(signedMessage, senderLog.getPublicKey())) {
          System.out.println("Problem encountered verifying PostMessage from " + sender + " - dropping on floor.");
          return;
        }

        PostMessage message = signedMessage.getMessage();

        if (message instanceof DeliveryRequestMessage) {
          processDeliveryRequestMessage((DeliveryRequestMessage) message);
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
          System.out.println("Found unknown Postmessage " + message + " - dropping on floor.");
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
    debug("Received delivery request from : " + message.getSender() + " to: " + message.getDestination());

    synchronized(bufferedData) {
      Vector userQueue = (Vector) bufferedData.get(message.getDestination());

      if (userQueue == null) {
        debug("Creating entry for: " + message.getDestination());

        userQueue = new Vector();
        bufferedData.put(message.getDestination(), userQueue);
        scribeService.join(message.getDestination().getAddress(), PostImpl.this, credentials);

        debug("Joined Scribe group rooted at " + message.getDestination().getAddress());
      }

      userQueue.addElement(message.getEncryptedMessage());
    }
  }

  /**
   * This method handles the receipt of a presence message, which
   * involves routing all pending notification messages to the
   * location advertised in the message.
   *
   * @param message The incoming message
   */
  private void processPresenceMessage(PresenceMessage message) {
    debug("Presence message from : " + message.getSender());

    synchronized (bufferedData) {
      Vector userQueue = (Vector) bufferedData.get(message.getSender());

      if (userQueue != null) {
        if (userQueue.size() == 0) {
          System.out.println(thePastryNode.getNodeId() + "DEBUG: ERROR - presence message from : " + message.getSender() + " has empty vector.");
        }

        for (int i=0; i<userQueue.size(); i++) {
          SignedPostMessage spm = (SignedPostMessage) userQueue.elementAt(i);
          DeliveryMessage dm = new DeliveryMessage(address, getNodeId(), spm);
          routeMsg(message.getLocation(), new PostPastryMessage(signPostMessage(dm)), getCredentials(), new SendOptions());
        }
      } else {
        System.out.println(thePastryNode.getNodeId() + "DEBUG: ERROR - presence message from : " + message.getSender() + " should not be received here.");
      }
    }
  }

  /**
   * This method processes a delivery message containing a notification message.
   *
   * @param message The incoming message.
   */
  private void processDeliveryMessage(DeliveryMessage message) {
    debug("Delivery message from : " + message.getSender());

    // send receipt
    ReceiptMessage rm = new ReceiptMessage(address, message.getEncryptedMessage());
    routeMsg(message.getLocation(), new PostPastryMessage(signPostMessage(rm)), getCredentials(), new SendOptions());

    // process internal message
    processSignedPostMessage(message.getEncryptedMessage());
  }
  
  /**
   * This method processes a notification message by passing it up
   * to the appropriate application.
   *
   * @param message The incoming message.
   */
  private void processEncryptedNotificationMessage(EncryptedNotificationMessage message) {
    debug("Encrypted notification message from : " + message.getSender());
    NotificationMessage nm = null;

    // decrypt and verify notification message
    try {
      byte[] key = SecurityUtils.decryptAsymmetric(message.getKey(), keyPair.getPrivate());
      nm = (NotificationMessage) SecurityUtils.deserialize(SecurityUtils.decryptSymmetric(message.getData(), key));
    } catch (Exception e) {
      System.out.println("yException occured which decrypting NotificationMessage " + e + " - dropping on floor.");
      return;
    }

    debug("Successfully deserialized notification message from : " + nm.getSender());

    if (! (nm.getSender().equals(message.getSender()))) {
      System.out.println("PANIC - Found ENM from " + message.getSender() + " with internal NM from different sender " +
                         nm.getSender() + " - dropping on floor.");
      return;
    }

    if (rice.pastry.Log.ifp(6))
      System.out.println(thePastryNode.getNodeId() + "DEBUG: successfully verified ENM with NM: " + nm);

    // deliver notification messag
    PostClient client = (PostClient) clientAddresses.get(nm.getClientAddress());

    if (client != null) {
      client.notificationReceived(nm);
    } else {
      System.out.println("Found notification message for unknown client " + client + " - dropping on floor.");
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
    debug("Received receipt message from : " + message.getSender());

    SignedPostMessage sm = message.getEncryptedMessage();
    PostEntityAddress sender = message.getSender();

    // remove message
    synchronized (bufferedData) {
      Vector userQueue = (Vector) bufferedData.get(sender);

      if (userQueue != null) {
        boolean success = userQueue.remove(sm);

        if (! success) {
          System.out.println("ERROR - Received receiptmessage for unknown message " + sm);
        }

        if (userQueue.size() == 0) {
          bufferedData.remove(sender);
          scribeService.leave(sender.getAddress(), PostImpl.this, credentials);
        }
      } else {
        scribeService.leave(sender.getAddress(), PostImpl.this, credentials);
      }
    }
  }

  /**
   * This method handles an incoming group multicast message.
   *
   * @param message The incoming message.
   */
  private void processGroupMessage(GroupNotificationMessage message) {
    PostGroupAddress destination = (PostGroupAddress) message.getGroup();

    debug("Received group message from: " + destination);

    byte[] key = (byte[]) keys.get(destination);

    debug("Using group key " + key + " for decryption.");

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
        System.out.println("Found notification message for unknown client " + client + " - dropping on floor.");
      }
    } catch (Exception e) {
      System.out.println("Exception occured while decrypting GroupNotificationMessage " + e + " - dropping on floor.");
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

    debug("Looking up postlog for : " + entity);

    storage.retrieveSigned(new SignedReference(entity.getAddress()), new StandardContinuation(command) {
      public void receiveResult(Object o) {
        final PostLog log = (PostLog) o;

        if (log == null) {
          debug("Could not find postlog for: " + entity);

          if (entity.equals(getEntityAddress())) {
            PostImpl.this.log = new PostLog(entity, keyPair.getPublic(), certificate, PostImpl.this, parent);
            return;
          } else {
            System.out.println("PostLog lookup for user " + entity + " failed.");
            
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

              debug("Successfully retrieved postlog for: " + entity);

              parent.receiveResult(log);
            } else  {
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
    if (rice.pastry.Log.ifp(6))
      System.out.println(thePastryNode.getNodeId() + "DEBUG: publishing presence to the group " + address.getAddress());

    PresenceMessage pm = new PresenceMessage(address, getNodeId());
    scribeService.multicast(address.getAddress(), new PostScribeMessage(signPostMessage(pm)), getCredentials());
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

    debug("Sending notification message " + message + " to: " + destination);

    getPostLog(destination, new ListenerContinuation("Send Notification to " + destination) {
      public void receiveResult(Object o) {
        PostLog destinationLog = (PostLog) o;

        if (destinationLog == null) {
          System.out.println("ERROR - Could not send notification message to non-existant user " + destination);
          return;
        }

        debug("Received destination log " + destinationLog);

        NodeId random = factory.generateNodeId();

        debug("Picked random node: " + random);

        byte[] cipherText = null;

        try {
          byte[] key = SecurityUtils.generateKeySymmetric();
          byte[] keyCipherText = SecurityUtils.encryptAsymmetric(key, destinationLog.getPublicKey());
          cipherText = SecurityUtils.encryptSymmetric(SecurityUtils.serialize(message), key);

          debug("Built encrypted notfn msg: " + destination);

          EncryptedNotificationMessage enm = new EncryptedNotificationMessage(address, keyCipherText, cipherText);
          DeliveryRequestMessage drm = new DeliveryRequestMessage(address, destination, signPostMessage(enm));

          debug("Sending delivery request to : " + random);

          routeMsg(random, new PostPastryMessage(signPostMessage(drm)), getCredentials(), new SendOptions());
        } catch (Exception e) {
          System.out.println("Exception occured which encrypting NotificationMessage " + e + " - aborting.");
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

    debug("Sending notification message " + message + " directly to " + destination + " via " + handle);

    getPostLog(destination, new ListenerContinuation("Send Notification Direct to " + destination + " via " + handle) {
      public void receiveResult(Object o) {
        PostLog destinationLog = (PostLog) o;

        if (destinationLog == null) {
          System.out.println("ERROR - Could not send notification message to non-existant user " + destination);
          return;
        }

        debug("Received destination log " + destinationLog);

        NodeId random = factory.generateNodeId();

        debug("Picked random node: " + random);

        byte[] cipherText = null;

        try {
          byte[] key = SecurityUtils.generateKeySymmetric();
          byte[] keyCipherText = SecurityUtils.encryptAsymmetric(key, destinationLog.getPublicKey());
          cipherText = SecurityUtils.encryptSymmetric(SecurityUtils.serialize(message), key);

          if (rice.pastry.Log.ifp(6))
            System.out.println(thePastryNode.getNodeId() + "DEBUG: built encrypted notfn msg: " + destination);

          EncryptedNotificationMessage enm = new EncryptedNotificationMessage(address, keyCipherText, cipherText);

          debug("Sending notification message directly to : " + handle);

          routeMsgDirect(handle, new PostPastryMessage(signPostMessage(enm)), getCredentials(), new SendOptions());
        } catch (Exception e) {
          System.out.println("Exception occured which encrypting NotificationMessage " + e + " - dropping on floor.");
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

    scribeService.join(address.getAddress(), this, credentials);
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

    debug("Sending message " + message + " to group " + destination);

    byte[] key = (byte[]) keys.get(destination);

    try {
      byte[] cipherText = null;

      if (key != null) {
        cipherText = SecurityUtils.encryptSymmetric(SecurityUtils.serialize(message), key);
      } else {
        cipherText = SecurityUtils.serialize(message);
      }

      GroupNotificationMessage gnm = new GroupNotificationMessage(address, destination, cipherText);

      debug("Built encrypted notfn msg " + gnm + " for destination " + destination);

      scribeService.multicast(destination.getAddress(), new PostScribeMessage(signPostMessage(gnm)), credentials);
    } catch (Exception e) {
      System.out.println("Exception occured while encrypting GroupNotificationMessage " + e + " - dropping on floor.");
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
      System.out.println("SecurityException " + e + " occured while siging PostMessage " + message + " - aborting.");
      return null;
    } catch (IOException e) {
      System.out.println("IOException " + e + " occured while siging PostMessage " + message + " - aborting.");
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
        System.out.println("Cannot verify PostMessage with null key!" + message + " " + key);
        return false;
      }
      
      byte[] plainText = SecurityUtils.serialize(message.getMessage());
      byte[] sig = message.getSignature();

      return SecurityUtils.verify(plainText, sig, key);
    } catch (SecurityException e) {
      System.out.println("SecurityException " + e + " occured while verifiying PostMessage " + message + " - aborting.");
      return false;
    } catch (IOException e) {
      System.out.println("IOException " + e + " occured while verifiying PostMessage " + message + " - aborting.");
      return false;
    }
  }

  public boolean anycastHandler(ScribeMessage msg) { return true; }
  public void faultHandler(ScribeMessage msg, NodeHandle faultyParent) {}
  public void forwardHandler(ScribeMessage msg) {}
  public void scribeIsReady() {}
  public void subscribeHandler(NodeId topicId, NodeHandle child, boolean wasAdded, Serializable obj ) {}
  public void isNewRoot(NodeId topicId) {}
  public void newParent(NodeId topicId, NodeHandle newParent, Serializable data) {}

  public String toString() {
    return "PostImpl[" + address + "]";
  }

  public void debug(String message) {
    if (rice.pastry.Log.ifp(6))
      System.out.println(thePastryNode.getNodeId() + " DEBUG: " + message);
  }
}
