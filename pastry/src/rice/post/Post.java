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
  private PostUserAddress address;
  
  
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
              PostUserAddress address,
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
    if(message instanceof NotificationMessage)
         handleNotificationMessage(message); 
    else if( message instanceof DeliveryRequestMessage)
         handleDeliveryRequestMessage(message); 
    else if( message instanceof ReceiptMessage ) {
         handleRecieptMessage(message);
    }
   
  }
  private void handleNotificationMessage(Message message){
        NotificationMessage nmessage = (NotificationMessage) message;
        PostClient client = (PostClient) clientAddresses.get(nmessage.getClientId());
        if(client != null){
          client.notificationReceived(nmessage);
     }
  }
  
  private void handleRecieptMessage(Message message){

  }

  private void handleDeliveryRequestMessage(Message message){
    /* Buffer this for Delivery */
    DeliveryRequestMessage dmessage = (DeliveryRequestMessage) message;
    synchronized(bufferedData){
    Vector userQueue = (Vector) bufferedData.get(dmessage.getDestination());
    if(userQueue == null){
       userQueue = new Vector();
       bufferedData.put(dmessage.getDestination(), userQueue);
    }
    userQueue.addElement(dmessage.getNotificationMessage());
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
      throw new PostException("Could not access PostLog: " + se);
    }
  }
  
  /**
   * @return The local storage service for POST, which writes data securely
   * to PAST.
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
   * 
   */
  private void announcePresence(){
     NodeHandle nodeId = thePastryNode.getLocalHandle();
     NodeId topicId = null; /* fix this to be the topic id of the user's scribe group */
     MessagePublish sMessage = 
                  new MessagePublish(getAddress(), nodeId, topicId, getCredentials());
    routeMsg(topicId, sMessage, getCredentials(), new SendOptions());
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

 // Notice:  This is not encrypted!!  (Fixme)
  public void sendNotification(NotificationMessage message) {
     NodeId destination = (new RandomNodeIdFactory()).generateNodeId();
     DeliveryRequestMessage drmessage = 
                   new DeliveryRequestMessage(address, (PostUserAddress) message.getAddress(), message);
     routeMsg(destination, drmessage, getCredentials(),
              new SendOptions());
  }  

  public void faultHandler(ScribeMessage msg, NodeHandle faultyParent) {}
  public void forwardHandler(ScribeMessage msg) {}
  public void receiveMessage(ScribeMessage msg) {

     synchronized(bufferedData){
 
          Vector userQueue = (Vector) bufferedData.get(msg.getDestination());
          if(userQueue != null){
  while(!userQueue.isEmpty()){
             NotificationMessage message = (NotificationMessage) userQueue.elementAt(0);
       userQueue.removeElementAt(0);  
                    routeMsg(message.getAddress().getAddress(), message, getCredentials(),
                             new SendOptions());
       userQueue.removeElementAt(0);  
  }
          }
     }
     /* Check and see if I have any messages for this person */
     /* If I do then call sendNotification over and over again */
     /* Until they are all delivered */

  }
  public void scribeIsReady(){}
  public void subscribeHandler(ScribeMessage msg,
                               NodeId topicId,
                               NodeHandle child,
                               boolean wasAdded){}
}
