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

/**
 * This class is the service layer which allows 
 * Post applications to use Post functionality.
 */
public class Post extends PastryAppl implements IScribeApp  {
  
  // the local PAST service
  private PASTService pastService;
  
  // the local Scribe service
  private IScribe scribeService;
  
  // the address of the local user
  private PostUserAddress address;
  
  // the keys of the local user
  private KeyPair keyPair;

  // --- PASTRY SUPPORT ---

  // the credentials of POST
  private Credentials credentials = new PermissiveCredentials();
  
  // --- CLIENT SUPPORT ---
  
  // the list of clients
  private Vector clients;
  
  // the map of PostClientAddress -> PostClient
  private Hashtable clientAddresses;

  // --- BUFFERING SUPPORT ---
  
  // The data structure to hold the packets
  private Hashtable bufferedData;

  // --- LOGGING SUPPORT ---

  // This user's log
  private PostLog log;

  // --- STORAGE SUPPORT ---

  // The storage service
  private StorageService storage;

  /**
   * Builds a PostService to run on the given pastry node,
   * using the provided PAST and Scribe services.
   *
   * @param node The Pastry node to run on.
   * @param past The PAST service running on this Pastry node.
   * @param scribe The Scribe service running on this Pastry node.
   * @param address The address of the user in the system
   * @param keyPair The KeyPair of this user
   */
  public Post(PastryNode node, 
              PASTService past, 
              IScribe scribe,
              PostUserAddress address,
              KeyPair keyPair) {
    super(node);
    
    pastService = past;
    scribeService = scribe;
    this.address = address;
    this.keyPair = keyPair;

    storage = new StorageService(past, credentials, keyPair);
    
    clients = new Vector();
    clientAddresses = new Hashtable();
    bufferedData = new Hashtable();
  }

  /**
   * Returns the PastryAddress of this application
   *
   * @return The PostAddress, unique to Post
   */
  public Address getAddress() {
    return PostAddress.instance();
  }
  
  /**
   * Returns the credentials of POST
   *
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
   * This method returns this user's PostLog, which is the root of all of
   * the user's application logs.
   *
   * @return This user's PostLog
   */
  public PostLog getLog() {
    return null;
  }

  /**
   * This method returns the local storage service, which writes data securely
   * to PAST.
   *
   * @return This POST's StorageService.
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
