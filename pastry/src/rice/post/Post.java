package rice.post;

import java.security.*;
import java.util.*;

import rice.pastry.security.*;
import rice.pastry.client.*;
import rice.pastry.messaging.*;
import rice.pastry.*;
import rice.past.*;
import rice.scribe.*;
import rice.post.log.*;
import rice.post.messaging.*;
import rice.post.storage.*;

/**
 * This class is the service layer which allows 
 * Post applications to use Post functionality.
 */
public class Post extends PastryAppl {
  
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
    
    clients = new Vector();
    clientAddresses = new Hashtable();
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
   * Removes a client from this PostService.  
   *
   * @param client The client to remove
   */
  public void removeClient(PostClient client) {
    clients.remove(client);
    clientAddresses.remove(client.getAddress());
  }

  /**
   * Inserts data into the Post system using persistent storage.
   * This first encrypts the PostData using it's hash value as the
   * key, and then stores the ciphertext at the value of the hash of
   * the ciphertext.
   *
   * @param message The PostData to store.
   * @return A reference to the stored PostData, containing both the key
   *         and address.
   */
  public PostDataReference insertData(PostData data) {
    return null;
  }
  
  /**
   * The method retrieves a given PostDataReference object from the
   * network. This method also performs the verification checks and
   * decryption necessary.
   *
   * @param reference The reference to the PostDataObject
   * @return The corresponding PostData object
   */
  public PostData retrieveData(PostDataReference reference) {
    return null;
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
     routeMsg(message.getAddress().getAddress(), message, getCredentials(), new SendOptions());
     // This is from the PastryAppl interface which Post inherits - notice that we're
     // using default SendOptions, which at some point we may want to vary
  }

  /**
   * This method appends an entry into the user's log, and updates the user's
   * LogHead in order to reflect the new object. This method returns a LogEntryReference
   * which is a pointer to the LogEntry in PAST.
   *
   * @param entry The log entry to append to the log.
   */
  public LogEntryReference addLogEntry(LogEntry entry) {
    return null;
  }
  
  /**
   * This method retrievess a log entry given a reference to the log entry.
   * This method also performs the appropriate verification checks and decryption
   * necessary.
   *
   * @param reference The reference to the log entry
   * @return The log entry referenced
   */
  public LogEntry retrieveLogEntry(LogEntryReference reference) {
    return null;
  }

  /**
   * This method retrievess a log head given a reference to the log head.
   * This method also performs the appropriate verification checks and decryption
   * necessary.
   *
   * @param reference The reference to the log head
   * @return The log head referenced
   */
  public LogHead retrieveLogHead(LogHeadReference reference) {
    return null;
  }
  
  /**
   * This method returns the header of the current user's logs, which
   * is represented by a UserBlockLogEntry.  This block has methods which
   * return the user's public key, identity, and pointers to all of the
   * user's logs.
   *
   * @return The UserBlockLogEntry which is currently at the top of the user's log
   */
  public UserBlock getUserBlock() {
    return null;
  }
}
