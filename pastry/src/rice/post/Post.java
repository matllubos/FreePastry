package rice.post;
 
import java.security.*;
import java.util.*;
import java.io.*;

import rice.pastry.security.*;
import rice.pastry.client.*;
import rice.pastry.messaging.*;
import rice.pastry.routing.*;
import rice.pastry.standard.*;
import rice.pastry.*;
import rice.past.*;
import rice.scribe.*;
import rice.scribe.messaging.*;
import rice.post.log.*;
import rice.post.messaging.*;
import rice.post.storage.*;
import rice.post.security.*;

/**
 * This class is the service layer which allows 
 * Post applications to use Post functionality.
 * 
 * @version $Id$
 */
public class Post extends PastryAppl implements IScribeApp  {
  
  /**
   * The local PAST service to use for persistent storage.
   */
  private PASTService pastService;
  
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
  private PublicKey publicKey;
  
  /**
   * The certificate used to authenticate this user's key pair.
   */
  private java.security.cert.Certificate certificate;
  
  /**
   * The public key of the certificate authority.
   */
  private PublicKey caPublicKey;
  

  /**
   * Builds a PostService to run on the given pastry node,
   * using the provided PAST and Scribe services.
   *
   * @param node The Pastry node to run on.
   * @param past The PAST service running on this Pastry node.
   * @param scribe The Scribe service running on this Pastry node.
   * @param address The address of the user in the system
   * @param keyPair The KeyPair of this user
   * @param cert The certificate authenticating this user
   * @param caPublicKey The public key of the certificate authority
   * 
   * @throws PostException if the PostLog could not be accessed
   */
  public Post(PastryNode node, 
              PASTService past, 
              IScribe scribe,
              PostEntityAddress address,
              KeyPair keyPair,
              java.security.cert.Certificate cert, 
              PublicKey caPublicKey)
    throws PostException
  {
    super(node);
    
    this.pastService = past;
    this.scribeService = scribe;
    this.address = address;
    this.publicKey = keyPair.getPublic();
    this.certificate = cert;
    this.caPublicKey = caPublicKey;

    security = new SecurityService(keyPair, caPublicKey);
    storage = new StorageService(past, credentials, security);
    
    // Try to get the post log
    this.log = null;
    retrievePostLog();
    
    clients = new Vector();
    clientAddresses = new Hashtable();
    bufferedData = new Hashtable();
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
   * The method by which Pastry passes a message up to POST
   *
   * @param message The message which has arrived
   */
  public void messageForAppl(Message message) {
    if (message instanceof PostMessageWrapper) {
      handlePostMessage(((PostMessageWrapper) message).getMessage());
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
    if (msg.getData() instanceof PostMessageWrapper) {
      handlePostMessage(((PostMessageWrapper) msg.getData()).getMessage());
    } else {
      System.out.println("Found unknown Scribe message " + msg.getData() + " - dropping on floor.");
    }
  }

  /**
   * Internal method for processing incoming PostMessage.  This
   * method performs all verification checks and processes the
   * message appropriately.
   *
   * @param message The incoming message.
   */
  private	void handlePostMessage(PostMessage message) {
    PostEntityAddress sender = message.getSender();
    PostLog senderLog = null;

    try {
      senderLog = getPostLog(sender);
    } catch (PostException e) {
      System.out.println("PostException occured while retrieving PostLog for " + sender + " - aborting.");
      return;
    }      

    // look up sender
    if (senderLog == null) {
      System.out.println("Found PostMessage from non-existent sender " + sender + " - dropping on floor.");
      return;
    }

    // verify messag is signed
    if (! verifyPostMessage(message, senderLog.getPublicKey())) {
      System.out.println("Problem encountered verifying PostMessage from " + sender + " - dropping on floor.");
      return;
    }

    if (message instanceof DeliveryRequestMessage) {
      handleDeliveryRequestMessage((DeliveryRequestMessage) message);
    } else if (message instanceof PresenceMessage) {
      handlePresenceMessage((PresenceMessage) message);
    } else if (message instanceof EncryptedNotificationMessage) {
      handleEncryptedNotificationMessage((EncryptedNotificationMessage) message);
    } else if (message instanceof ReceiptMessage) {
      handleRecieptMessage((ReceiptMessage) message);
    } else {
      System.out.println("Found unknown Postmessage " + message + " - dropping on floor.");
    }
  }
  
  /**
   * This method handles the processing of a delivery
   * request message by subscribing the appriate scribe
   * group.
   *
   * @param message The incoming message.
   */
  private void handleDeliveryRequestMessage(DeliveryRequestMessage message){
    /* Buffer this for Delivery */

    synchronized(bufferedData) {
      Vector userQueue = (Vector) bufferedData.get(message.getDestination());

      if (userQueue == null) {
        userQueue = new Vector();
        bufferedData.put(message.getDestination(), userQueue);
        scribeService.join(message.getDestination().getAddress(), this, credentials);
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
  private void handlePresenceMessage(PresenceMessage message) {
    synchronized (bufferedData) {

      Vector userQueue = (Vector) bufferedData.get(message.getSender());

      if (userQueue != null) {
        for (int i=0; i<userQueue.size(); i++) {
          EncryptedNotificationMessage enm = (EncryptedNotificationMessage) userQueue.elementAt(i);

          signAndPreparePostMessage(enm);
          
          routeMsg(message.getLocation(), new PostPastryMessage(enm), getCredentials(), new SendOptions());
        }
      }
    }
  }
    
  
  /**
   * This method processes a notification message by passing it up
   * to the appropriate application.
   *
   * @param message The incoming message.
   */
  private void handleEncryptedNotificationMessage(EncryptedNotificationMessage message) {
    // send receipt
    ReceiptMessage rm = new ReceiptMessage(address, message);
    signAndPreparePostMessage(rm);
    routeMsg(message.getSender().getAddress(), new PostPastryMessage(rm), getCredentials(), new SendOptions());

    NotificationMessage nm = null;
    
    // decrypt and verify notification message
    try {
      nm = (NotificationMessage) security.deserialize(security.decryptRSA(message.getData()));
    } catch (SecurityException e) {
      System.out.println("SecurityException occured which decrypting NotificationMessage " + e + " - dropping on floor.");
      return;
    } catch (IOException e) {
      System.out.println("IOException occured which decrypting NotificationMessage " + e + " - dropping on floor.");
      return;
    } catch (ClassNotFoundException e) {
      System.out.println("ClassNotFoundException occured which decrypting NotificationMessage " + e + " - dropping on floor.");
      return;
    } catch (ClassCastException e) {
      System.out.println("ClassCastException occured which decrypting NotificationMessage " + e + " - dropping on floor.");
      return;
    }
    
    PostEntityAddress sender = nm.getSender();
    PostLog senderLog = null;

    try {
      senderLog = getPostLog(sender);
    } catch (PostException e) {
      System.out.println("PostException occured while retrieving PostLog for " + sender + " - aborting.");
      return;
    }    
    
    // look up sender
    if (senderLog == null) {
      System.out.println("Found NotificationMessage from non-existent sender " + sender + " - dropping on floor.");
      return;
    }
    
    if (! verifyPostMessage(nm, senderLog.getPublicKey())) {
      System.out.println("Error occured during verification of notification message " + nm + " - dropping on floor.");
      return;
    }

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
  private void handleRecieptMessage(ReceiptMessage message) {
    EncryptedNotificationMessage enm = message.getEncryptedNotificationMessage();
    PostEntityAddress sender = message.getSender();

    // remove message
    synchronized (bufferedData) {
      Vector userQueue = (Vector) bufferedData.get(sender);
      userQueue.remove(enm);
    }
  }

  /**
   * @return This user's PostLog, which is the root of all the user's
   * application logs.
   */
  public PostLog getLog() {
    return log;
  }
  
  /**
   * @return The PostLog belonging to the given entity, eg. to acquire
   * another user's public key.
   */
  public PostLog getPostLog(PostEntityAddress entity) throws PostException {
    try {
      SignedReference logRef = new SignedReference(entity.getAddress());
      PostLog postLog = (PostLog) storage.retrieveSigned(logRef);

      if (postLog == null) {
        return null;
      }
      
      if (security.verifyCertificate(postLog.getEntityAddress(),
                                     postLog.getPublicKey(),
                                     postLog.getCertificate())) {
        return postLog;
      }
      else {
        throw new PostException("Certificate of PostLog could not verified" +
                                " for entity: " + entity);
      }
    }
    catch (StorageException se) {
      throw new PostException("Could not access PostLog: " + se);
    }
  }

  /**
   * Retrieve's this user's PostLog from PAST, or creates and stores a new one
   * if one does not already exist.
   */
  private void retrievePostLog() throws PostException {
    try {
      PostLog postLog = getPostLog(address);
      if (postLog == null) {
        // None found, so create a new one
        postLog = new PostLog(address, publicKey, certificate);
        storage.storeSigned(postLog, address.getAddress());
      }
      
      // Store the log in a field
      this.log = postLog;
    }
    catch (StorageException se) {
      se.printStackTrace();
      throw new PostException("Could not access PostLog: " + se + " " + se.getMessage());
    }
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
  private void announcePresence(){
    PresenceMessage pm = new PresenceMessage(address, getNodeId());
    signAndPreparePostMessage(pm);

    scribeService.multicast(address.getAddress(), new PostScribeMessage(pm), getCredentials());
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
  public void sendNotification(NotificationMessage message) {
    NodeId random = (new RandomNodeIdFactory()).generateNodeId();

    // TO DO : Assuming just a user for now, grouping crap later...
    PostUserAddress destination = (PostUserAddress) message.getDestination();
    PostLog destinationLog = null;

    try {
      destinationLog = getPostLog(destination);
    } catch (PostException e) {
      System.out.println("PostException occured while retrieving PostLog for " + destination + " - aborting.");
      return;
    }   	 	
    
    signAndPreparePostMessage(message);

    byte[] cipherText = null;
    
    try {
      cipherText = security.encryptRSA(security.serialize(message), destinationLog.getPublicKey());
    } catch (SecurityException e) {
      System.out.println("SecurityException occured which encrypting NotificationMessage " + e + " - dropping on floor.");
      return;
    } catch (IOException e) {
      System.out.println("IOException occured which encrypting NotificationMessage " + e + " - dropping on floor.");
      return;
    } 

    EncryptedNotificationMessage enm = new EncryptedNotificationMessage(address, cipherText);
    DeliveryRequestMessage drm = new DeliveryRequestMessage(address, destination, enm);

    signAndPreparePostMessage(drm);
    
    routeMsg(random, new PostPastryMessage(drm), getCredentials(), new SendOptions());
  }

  /**
   * Internal utility method for preparing a PostMessage for transmission.  This
   * method signs the message and tells the message that it is about to be sent
   * across the wire.  NOTE: The message should NOT be changes after calling this
   * method, and this method should ALWAYS be called before transmitting a PostMessage.
   *
   * @param message The message to prepare.
   */
  private void signAndPreparePostMessage(PostMessage message) {
    try {
      byte[] sig = security.sign(security.serialize(message));

      message.setSignature(sig);
      message.prepareForSend();
    } catch (SecurityException e) {
      System.out.println("SecurityException " + e + " occured while siging PostMessage " + message + " - aborting.");
      return;
    } catch (IOException e) {
      System.out.println("IOException " + e + " occured while siging PostMessage " + message + " - aborting.");
      return;
    } 
  }

  /**
   * This method verifies that a given post message was signed by the user
   * corresponding the to given public key.
   *
   * @param message The message to verify.
   * @param key The key to verify against.
   */
  private boolean verifyPostMessage(PostMessage message, PublicKey key) {
    try {
      byte[] plainText = security.serialize(message);
      byte[] sig = message.getSignature();

      return security.verify(plainText, sig, key);
    } catch (SecurityException e) {
      System.out.println("SecurityException " + e + " occured while verifiying PostMessage " + message + " - aborting.");
      return false;
    } catch (IOException e) {
      System.out.println("IOException " + e + " occured while verifiying PostMessage " + message + " - aborting.");
      return false;
    }
  }

  public void faultHandler(ScribeMessage msg, NodeHandle faultyParent) {}
  public void forwardHandler(ScribeMessage msg) {}
  public void scribeIsReady(){}
  public void subscribeHandler(ScribeMessage msg,
                               NodeId topicId,
                               NodeHandle child,
                               boolean wasAdded){}
}
