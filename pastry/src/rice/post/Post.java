package rice.post;
 
import java.security.*;
import java.util.*;
import java.io.*;

import rice.*;
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
 * @author Ansley Post
 * @author Alan Mislove
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
  private PublicKey publicKey;
  
  /**
   * The certificate used to authenticate this user's key pair.
   */
  private PostCertificate certificate;
  
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
   * @param certificate The certificate authenticating this user
   * @param caPublicKey The public key of the certificate authority
   * @param instance The unique instance name of this POST
   * 
   * @throws PostException if the PostLog could not be accessed
   */
  public Post(PastryNode node, 
              PASTService past, 
              IScribe scribe,
              PostEntityAddress address,
              KeyPair keyPair,
              PostCertificate certificate, 
              PublicKey caPublicKey,
              String instance)
    throws PostException
  {
    super(node, instance);
    
    this.pastService = past;
    this.scribeService = scribe;
    this.address = address;
    this.publicKey = keyPair.getPublic();
    this.certificate = certificate;
    this.caPublicKey = caPublicKey;

    security = new SecurityService(keyPair, caPublicKey);
    storage = new StorageService(address, past, credentials, security);
    factory = new RandomNodeIdFactory();

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
  private void processSignedPostMessage(SignedPostMessage message) {
    ProcessSignedPostMessageTask task = new ProcessSignedPostMessageTask(message);
    task.start();
  }
  
  /**
   * This method processs the processing of a delivery
   * request message by subscribing the appriate scribe
   * group.
   *
   * @param message The incoming message.
   */
  private void processDeliveryRequestMessage(DeliveryRequestMessage message){
    ProcessDeliveryRequestMessageTask task = new ProcessDeliveryRequestMessageTask(message);
    task.start();
  }

  /**
   * This method handles the receipt of a presence message, which
   * involves routing all pending notification messages to the
   * location advertised in the message.
   *
   * @param message The incoming message
   */
  private void processPresenceMessage(PresenceMessage message) {
    ProcessPresenceMessageTask task = new ProcessPresenceMessageTask(message);
    task.start();
  }

  /**
   * This method processes a delivery message containing a notification message.
   *
   * @param message The incoming message.
   */
  private void processDeliveryMessage(DeliveryMessage message) {
    ProcessDeliveryMessageTask task = new ProcessDeliveryMessageTask(message);
    task.start();
  }
  
  /**
   * This method processes a notification message by passing it up
   * to the appropriate application.
   *
   * @param message The incoming message.
   */
  private void processEncryptedNotificationMessage(EncryptedNotificationMessage message) {
    ProcessEncryptedNotificationMessageTask task = new ProcessEncryptedNotificationMessageTask(message);
    task.start();
  }

  /**
   * This method handles an incoming receipt message by
   * removing the soft state associated with the delivery
   * request.
   *
   * @param message The incoming message.
   */
  private void processReceiptMessage(ReceiptMessage message) {
    ProcessReceiptMessageTask task = new ProcessReceiptMessageTask(message);
    task.start();
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
  public void getPostLog(PostEntityAddress entity, Continuation command) {
    if ((entity.equals(getEntityAddress())) && (log != null)) {
      command.receiveResult(log);
    } else {
      RetrievePostLogTask task = new RetrievePostLogTask(entity, command);
      task.start();
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
  public void sendNotification(NotificationMessage message) {
    SendNotificationMessageTask task = new SendNotificationMessageTask(message);
    task.start();
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
      PostSignature sig = security.sign(security.serialize(message));

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
      
      byte[] plainText = security.serialize(message.getMessage());
      PostSignature sig = message.getSignature();

      return security.verify(plainText, sig, key);
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

  /**
   * This class is called whenever a SignedPostMessage comes in, and it
   * performs the necessary verification tasks and then handles the
   * message.
   */
  protected class ProcessSignedPostMessageTask implements Continuation {

    private SignedPostMessage signedMessage;
    private PostEntityAddress sender;
    private PostLog senderLog;

    /**
     * Constructs a ProcessPostMessageTask given a message.
     */
    public ProcessSignedPostMessageTask(SignedPostMessage signedMessage) {
      this.signedMessage = signedMessage;
    }

    /**
     * Starts the processing of this message.
     */
    public void start() {
      sender = signedMessage.getMessage().getSender();
      getPostLog(sender, this);
    }

    public void receiveResult(Object o) {
      senderLog = (PostLog) o;

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
      } else {
        System.out.println("Found unknown Postmessage " + message + " - dropping on floor.");
      }
    }

    public void receiveException(Exception e) {
      System.out.println("Exception " + e + "  occured during handling of SignedPostMessage: " + signedMessage + " - dropping on floor.");
    }
  }

  /**
   * This class is called whenever a DeliveryRequestMessage comes in, and it
   *  handles the message.
   */
  protected class ProcessDeliveryRequestMessageTask {

    private DeliveryRequestMessage message;

    /**
      * Constructs a ProcessPostMessageTask given a message.
     */
    public ProcessDeliveryRequestMessageTask(DeliveryRequestMessage message) {
      this.message = message;
    }

    /**
      * Starts the processing of this message.
     */
    public void start() {
      /* Buffer this for Delivery */

      if (rice.pastry.Log.ifp(6))
        System.out.println(thePastryNode.getNodeId() + "DEBUG: received delivery request from : " + message.getSender() + " to: " + message.getDestination());

      synchronized(bufferedData) {
        Vector userQueue = (Vector) bufferedData.get(message.getDestination());

        if (userQueue == null) {
          if (rice.pastry.Log.ifp(6))
            System.out.println(thePastryNode.getNodeId() + "DEBUG: creating entry for: " + message.getDestination());
          
          userQueue = new Vector();
          bufferedData.put(message.getDestination(), userQueue);
          scribeService.join(message.getDestination().getAddress(), Post.this, credentials);

          if (rice.pastry.Log.ifp(6))
            System.out.println(thePastryNode.getNodeId() + "DEBUG: joined Scribe group rooted at " + message.getDestination().getAddress());
        }

        userQueue.addElement(message.getEncryptedMessage());
      }
    }
  }

  /**
   * This class is called whenever a PresenceMessage comes in, and it
   *  handles the message.
   */
  protected class ProcessPresenceMessageTask {

    private PresenceMessage message;

    /**
      * Constructs a ProcessPostMessageTask given a message.
     */
    public ProcessPresenceMessageTask(PresenceMessage message) {
      this.message = message;
    }

    /**
      * Starts the processing of this message.
     */
    public void start() {

      if (rice.pastry.Log.ifp(6))
        System.out.println(thePastryNode.getNodeId() + "DEBUG: presence message from : " + message.getSender());

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
  }

  /**
   * This class is called whenever a DeliveryMessage comes in, and it
   *  handles the message.
   */
  protected class ProcessDeliveryMessageTask {

    private DeliveryMessage message;

    /**
      * Constructs a ProcessDeliveryMessageTask given a message.
     */
    public ProcessDeliveryMessageTask(DeliveryMessage message) {
      this.message = message;
    }

    /**
      * Starts the processing of this message.
     */
    public void start() {
      if (rice.pastry.Log.ifp(6))
        System.out.println(thePastryNode.getNodeId() + "DEBUG: delivery message from : " + message.getSender());

      // send receipt
      ReceiptMessage rm = new ReceiptMessage(address, message.getEncryptedMessage());
      routeMsg(message.getLocation(), new PostPastryMessage(signPostMessage(rm)), getCredentials(), new SendOptions());

      // process internal message
      processSignedPostMessage(message.getEncryptedMessage());
    }
  }
      

  /**
   * This class is called whenever a EncryptedNotificationMessage comes in, and it
   *  handles the message.
   */
  protected class ProcessEncryptedNotificationMessageTask {

    private EncryptedNotificationMessage message;
    private NotificationMessage nm;
    private PostEntityAddress sender;

    /**
    * Constructs a ProcessEncryptedNotificationMessageTask given a message.
     */
    public ProcessEncryptedNotificationMessageTask(EncryptedNotificationMessage message) {
      this.message = message;
    }

    /**
      * Starts the processing of this message.
     */
    public void start() {
      if (rice.pastry.Log.ifp(6))
        System.out.println(thePastryNode.getNodeId() + "DEBUG: encrypted notification message from : " + message.getSender());

      // decrypt and verify notification message
      try {
        byte[] key = security.decryptRSA(message.getKey());
        nm = (NotificationMessage) security.deserialize(security.decryptDES(message.getData(), key));
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

      System.out.println(thePastryNode.getNodeId() + "DEBUG: successfully deserialized notification message from : " + nm.getSender());

      if (! (nm.getSender().equals(message.getSender()))) {
        System.out.println("PANIC - Found ENM from " + message.getSender() + " with internal NM from different sender " +
                           nm.getSender() + " - dropping on floor.");
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
  }

  /**
    * This class is called whenever a RecieptMessage comes in, and it
   *  handles the message.
   */
  protected class ProcessReceiptMessageTask {

    private ReceiptMessage message;
    private NotificationMessage nm;
    private PostEntityAddress sender;

    /**
      * Constructs a ProcessRecieptMessageTask given a message.
     */
    public ProcessReceiptMessageTask(ReceiptMessage message) {
      this.message = message;
    }

    /**
      * Starts the processing of this message.
     */
    public void start() {

      if (rice.pastry.Log.ifp(6))
        System.out.println(thePastryNode.getNodeId() + "DEBUG: received receipt message from : " + message.getSender());
      
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
            scribeService.leave(sender.getAddress(), Post.this, credentials);
          }
        } else {
          scribeService.leave(sender.getAddress(), Post.this, credentials);
        }
      }
    }
  }

  /**
   * This class is a task which returns a PostLog to the callee.
   */
  protected class RetrievePostLogTask implements Continuation {

    private PostEntityAddress address;
    private Continuation command;

    /**
     * Constructs a task which will call the given command once the result
     * is available.
     */
    public RetrievePostLogTask(PostEntityAddress address, Continuation command) {
      this.address = address;
      this.command = command;
    }

    /**
     * Starts this task running.
     */
    public void start() {
      SignedReference logRef = new SignedReference(address.getAddress());
      storage.retrieveSigned(logRef, this);

      // Now we wait for the storage service to return the result
      // to us.
    }

    /**
     * Called when the result of a previous call is ready for
     * processing.
     *
     * @param o The result.
     */
    public void receiveResult(Object o) {
      try {
        PostLog log = (PostLog) o;

        if (log == null) {
          if (address.equals(getEntityAddress())) {
            Post.this.log = new PostLog(address, publicKey, certificate, Post.this, command);
            return;
          } else {
            if (rice.pastry.Log.ifp(6))
              System.out.println("PostLog lookup for user " + address + " failed.");
            command.receiveResult(null);
            return;
          }
        }

        if ((log.getPublicKey() == null) || (log.getEntityAddress() == null)) {
          command.receiveException(new PostException("Malformed PostLog: " + log.getPublicKey() + " " + log.getEntityAddress()));
          return;
        }

        if (! (log.getEntityAddress().equals(log.getCertificate().getAddress()) &&
               log.getPublicKey().equals(log.getCertificate().getKey()))) {
          command.receiveException(new PostException("Malformed PostLog: Certificate does not match log owner."));
          return;
        }
        
        if (security.verifyCertificate(caPublicKey, log.getCertificate())) {
          storage.verifySigned(log, log.getPublicKey());

          log.setPost(Post.this);

          if (address.equals(getEntityAddress())) {
            Post.this.log = log;
          }
          
          command.receiveResult(log);
        } else {
          command.receiveException(new PostException("Certificate of PostLog could not verified for entity: " + address));
        }
      } catch (ClassCastException e) {
        command.receiveException(new PostException("Received unknown value " + o + " from retrievePostLog."));
      }
    }

    /**
      * Called when a previously requested result causes an exception
     *
     * @param result The exception caused
     */
    public void receiveException(Exception result) {
      command.receiveException(result);
    }
  }

  /**
   * This class is called whenever a PostMessage comes in, and it
   * performs the necessary verification tasks and then handles the
   * message.
   */
  protected class SendNotificationMessageTask implements Continuation {

    private NotificationMessage message;
    private PostUserAddress destination;
    
    /**
      * Constructs a ProcessPostMessageTask given a message.
     */
    public SendNotificationMessageTask(NotificationMessage message) {
      this.message = message;
    }

    /**
      * Starts the processing of this message.
     */
    public void start() {
      // TO DO : Assuming just a user for now, grouping crap later...
      destination = (PostUserAddress) message.getDestination();

      if (rice.pastry.Log.ifp(6))
        System.out.println(thePastryNode.getNodeId() + "DEBUG: sending message to: " + destination);

      getPostLog(destination, this);
    }

    public void receiveResult(Object o) {
      PostLog destinationLog = (PostLog) o;

      if (destinationLog == null) {
        System.out.println("ERROR - Could not send notification message to non-existant user " + destination);
        return;
      }

      if (rice.pastry.Log.ifp(6))
        System.out.println(thePastryNode.getNodeId() + "DEBUG: received destination log");
      
      NodeId random = factory.generateNodeId();

      if (rice.pastry.Log.ifp(6))
        System.out.println(thePastryNode.getNodeId() + "DEBUG: picked random node: " + random);

      byte[] cipherText = null;

      try {
        byte[] key = security.generateKeyDES();
        byte[] keyCipherText = security.encryptRSA(key, destinationLog.getPublicKey());
        cipherText = security.encryptDES(security.serialize(message), key);

        if (rice.pastry.Log.ifp(6))
          System.out.println(thePastryNode.getNodeId() + "DEBUG: built encrypted notfn msg: " + destination);

        EncryptedNotificationMessage enm = new EncryptedNotificationMessage(address, keyCipherText, cipherText);
        DeliveryRequestMessage drm = new DeliveryRequestMessage(address, destination, signPostMessage(enm));

        if (rice.pastry.Log.ifp(6))
          System.out.println(thePastryNode.getNodeId() + "DEBUG: sending delivery request to : " + random);

        routeMsg(random, new PostPastryMessage(signPostMessage(drm)), getCredentials(), new SendOptions());
      } catch (SecurityException e) {
        System.out.println("SecurityException occured which encrypting NotificationMessage " + e + " - dropping on floor.");
      } catch (IOException e) {
        System.out.println("IOException occured which encrypting NotificationMessage " + e + " - dropping on floor.");
      }
    }

    public void receiveException(Exception e) {
      System.out.println("Exception " + e + "  occured during sending NotificationMessage: " + message + " - dropping on floor.");
    }
  }
}
