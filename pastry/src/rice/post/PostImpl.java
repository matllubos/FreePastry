package rice.post;

import java.io.*; 
import java.security.*;
import java.util.*;
import java.util.logging.* ;

import rice.*;
import rice.Continuation.*;

import rice.p2p.commonapi.*;
import rice.p2p.past.*;
import rice.p2p.replication.manager.*;
import rice.p2p.scribe.*;

import rice.persistence.*;

import rice.post.delivery.*;
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
public class PostImpl implements Post, Application, ScribeClient {

  /**
   * The replication factor to use for replicating delivery messages
   */
  public static final int REPLICATION_FACTOR = 3;
  
  /**
   * The interval between log refreshes
   */
  public static int BACKUP_INTERVAL = 1000 * 60 * 60 * 6;
  public static int SYNCHRONIZE_WAIT = 1000 * 60 * 3;

  /**
   * The endpoint used for routing messages
   */
  protected Endpoint endpoint;
  
  /**
   * The local Scribe service to use for notification.
   */
  protected Scribe scribe;
  
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
   * Caches PostEntityAddress to PostLog.
   */
  private Hashtable postLogs;
  
  /**
   * Maps PostEntityAddress to list of pending continuations
   */
  private Hashtable pendingLogs;
  

  // --- GROUP SUPPORT ---

  /**
   * The list of group keys known by this Post
   */
  private HashMap keys;

  
  // --- LOGGING SUPPORT ---

  /**
   * The top level POST log, with pointers to the logs for each application.
   */
  private PostLog log;
  
  
  // --- DELIVERY SUPPORT ---
  
  /**
   * The service which maintians the list of pending deliveries, and delivers messages
   */
  private DeliveryService delivery;
  
  /**
   * The buffer of incoming delivery messages
   */
  private Vector deliveryBuffer;

  
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
   * Whether POST is allowed to reinsert the POST log (should not normally be set)
   */
  private boolean logRewrite;
  
  /**
   * The previous address of the user, only used when re-inserting the new log
   */
  private PostEntityAddress previousAddress;
  

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
                  Past immutablePast,
                  Past mutablePast,
                  DeliveryPast deliveryPast,
                  Past deliveredPast,
                  PostEntityAddress address,
                  KeyPair keyPair,
                  PostCertificate certificate,
                  PublicKey caPublicKey,
                  String instance,
                  boolean logRewrite,
                  PostEntityAddress previousAddress,
                  long synchronizeInterval,
                  long refreshInterval,
                  long timeoutInterval) throws PostException 
  {
    this.endpoint = node.registerApplication(this, instance);
    this.address = address;
    this.keyPair = keyPair;
    this.certificate = certificate;
    this.caPublicKey = caPublicKey;
    this.logRewrite = logRewrite;
    this.previousAddress = previousAddress;
    
    this.scribe = new ScribeImpl(node, instance);
    this.delivery = new DeliveryService(this, deliveryPast, deliveredPast, scribe, node.getIdFactory(), timeoutInterval);
    this.deliveryBuffer = new Vector();

    this.security = new SecurityService();
    this.security.loadModule(new CASecurityModule(caPublicKey));
    this.storage = new StorageService(endpoint, address, immutablePast, mutablePast, node.getIdFactory(), keyPair, timeoutInterval);

    clients = new Vector();
    clientAddresses = new Hashtable();
    postLogs = new Hashtable();
    pendingLogs = new Hashtable();
    keys = new HashMap();
    
  //  logger.addHandler(new ConsoleHandler());
  //  logger.setLevel(Level.FINEST);
  //  logger.getHandlers()[0].setLevel(Level.FINEST);
    
    endpoint.scheduleMessage(new SynchronizeMessage(), SYNCHRONIZE_WAIT + new Random().nextInt((int) synchronizeInterval), synchronizeInterval);
    endpoint.scheduleMessage(new RefreshMessage(), new Random().nextInt((int) refreshInterval), refreshInterval);
    endpoint.scheduleMessage(new BackupMessage(), new Random().nextInt((int) BACKUP_INTERVAL), BACKUP_INTERVAL);
    
    logger.fine(endpoint.getId() + ": Constructed new Post with user " + address + " and instance " + instance);
  }
  
  /**
   * @return The logger, used to log messages
   */
  public Logger getLogger() {
    return logger;
  }
  
  /**
    * @return The Endpoint
   */
  public Endpoint getEndpoint() {
    return endpoint;
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
      if (((SignedPostMessageWrapper) message).getMessage().getMessage() instanceof DeliveryMessage)
        processDeliveryMessage((DeliveryMessage) ((SignedPostMessageWrapper) message).getMessage().getMessage(), 
                               new ListenerContinuation("Processing of Pastry Delivery POST Message"));
      else  
        processSignedPostMessage(((SignedPostMessageWrapper) message).getMessage(), new ListenerContinuation("Processing of Pastry POST Message"));
    } else if (message instanceof SynchronizeMessage) {
      processSynchronizeMessage((SynchronizeMessage) message);
    } else if (message instanceof RefreshMessage) {
      processRefreshMessage((RefreshMessage) message);
    } else if (message instanceof BackupMessage) {
      processBackupMessage((BackupMessage) message);
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
      processSignedPostMessage(((SignedPostMessageWrapper) content).getMessage(), new ListenerContinuation("Processing of Scribe POST message"));
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
   * @param command THe command to return the success value to
   */
  private void processSignedPostMessage(final SignedPostMessage signedMessage, Continuation command) {
    final PostEntityAddress sender = signedMessage.getMessage().getSender();
    logger.finer(endpoint.getId() + ": Processing signed message " + signedMessage + " from sender " + sender + " with address " + sender.getAddress());
    
    getPostLog(sender, new StandardContinuation(command) {
      public void receiveResult(Object o) {
        PostLog senderLog = (PostLog) o;

        // look up sender
        if (senderLog == null) {
          logger.warning(endpoint.getId() + ": Found PostMessage from non-existent sender " + sender + " - dropping on floor.");
          parent.receiveException(new PostException("Found PostMessage from non-existent sender " + sender + " - dropping on floor."));
          return;
        }
        
        PostMessage message = signedMessage.getMessage();

        // verify message is signed
        if (! verifySignedPostMessage(signedMessage, senderLog.getPublicKey())) {
          logger.warning(endpoint.getId() + ": Problem encountered verifying " + message.getClass().getName() + " from " + sender + " - dropping on floor.");
          parent.receiveException(new PostException("Problem encountered verifying " + message.getClass().getName() + " from " + sender + " - dropping on floor. (ourkey: " + keyPair.getPublic() + " senderkey: " + senderLog.getPublicKey() + ")"));
          return;
        }

        if (message instanceof PresenceMessage) {
          processPresenceMessage((PresenceMessage) message, parent);
        } else if (message instanceof EncryptedNotificationMessage) {
          processEncryptedNotificationMessage((EncryptedNotificationMessage) message, parent);
        } else if (message instanceof GroupNotificationMessage) {
          processGroupMessage((GroupNotificationMessage) message, parent);
        } else {
          logger.warning(endpoint.getId() + ": Found unknown Postmessage " + message + " - dropping on floor.");
          parent.receiveException(new PostException("Found unknown Postmessage " + message + " - dropping on floor."));
        }
      }
    });
  }

  /**
   * This method handles the receipt of a presence message, which
   * involves routing all pending notification messages to the
   * location advertised in the message.
   *
   * @param message The incoming message
   */
  private void processPresenceMessage(final PresenceMessage message, Continuation command) {
    logger.fine(endpoint.getId() + ": Presence message from : " + message.getSender() + " at " + message.getHandle());

    delivery.presence(message, new StandardContinuation(command) {
      public void receiveResult(Object o) {
        if (o != null) {
          DeliveryMessage dm = new DeliveryMessage(address, message.getSender(), (SignedPostMessage) o);
          endpoint.route(message.getLocation(), new PostPastryMessage(signPostMessage(dm)), message.getHandle());
        }
        
        parent.receiveResult(new Boolean(true));
      }
    });
  }

  /**
   * This method processes a delivery message containing a notification message.
   *
   * @param message The incoming message.
   */
  private void processDeliveryMessage(final DeliveryMessage message, final Continuation command) {
    logger.fine(endpoint.getId() + ": Delivery message from : " + message.getSender());

    if (! message.getDestination().equals(address)) {
      logger.finer(endpoint.getId() + ": Received delivery message at "  + address + " for " + message.getDestination());
      command.receiveResult(new Boolean(false));
      return;
    }
    
    Runnable buffered = new Runnable() {
      public void next() {
        Runnable run = null;
        
        synchronized (deliveryBuffer) {
          if ((deliveryBuffer.size() > 0) && (deliveryBuffer.get(0) == this)) {
            deliveryBuffer.remove(0);
            
            if (deliveryBuffer.size() > 0) {
              run = (Runnable) deliveryBuffer.get(0);
            }
          }
        } 
        
        if (run != null)
          run.run();
      }
      
      public void run() {
        delivery.check(message.getEncryptedMessage(), new StandardContinuation(command) {
          public void receiveResult(Object o) {
            if (((Boolean) o).booleanValue()) {
              logger.fine(endpoint.getId() + ": Haven't seen message " + message + " before - accepting");
              
              processSignedPostMessage(message.getEncryptedMessage(), new StandardContinuation(parent) {
                public void receiveResult(Object o) {
                  if (o.equals(Boolean.TRUE)) {
                    byte[] sig = signPostMessage(message.getEncryptedMessage().getMessage()).getSignature();
                    delivery.delivered(message.getEncryptedMessage(), sig, new StandardContinuation(parent) {
                      public void receiveResult(Object o) {
                        parent.receiveResult(o);
                        next();
                      }
                      
                      public void receiveException(Exception e) {
                        parent.receiveException(e);
                        next();
                      }
                    });
                  } else {
                    System.out.println("Was told not to accept Notification message " + message.getEncryptedMessage() + " - skipping (val " + o + ")");
                    next();
                  }
                }
                
                public void receiveException(final Exception e) {
                  if (e instanceof PostException) {
                    System.out.println("ERROR: Marking message " + message + " as undeliverable due to exception " + e);
                    delivery.undeliverable(message.getEncryptedMessage(), new StandardContinuation(parent) {
                      public void receiveResult(Object o) {
                        parent.receiveException(e);
                        next();
                      }
                      
                      public void receiveException(Exception e) {
                        parent.receiveException(e);
                        next();
                      }
                    });
                  } else {
                    System.out.println("ERROR: Received exception " + e + " processing delivery " + message + " - ignoring.");
                    parent.receiveException(e);
                    next();
                  }
                }
              });
            } else {
              logger.fine(endpoint.getId() + ": Seen message " + message + " before - ignoring");
              parent.receiveResult(new Boolean(true));
              next();
            }   
          }
          
          public void receiveException(Exception e) {
            parent.receiveException(e);
            next();
          }
        });
      }
    };

    boolean go = false;
    
    synchronized (deliveryBuffer) {
      deliveryBuffer.add(buffered);
      
      if (deliveryBuffer.size() == 1) 
        go = true;
    }
    
    if (go)
      buffered.run();
  }
  
  /**
   * This method processes a message to refresh all objects in the GCPast store.
   *
   * @param message The incoming message.
   */
  private void processRefreshMessage(RefreshMessage message) {
    final Iterator i = clients.iterator();
    System.out.println("BEGINNING REFRESH!");
    
    Continuation c = new ListenerContinuation("Retrieval of ContentHashReferences") {
      protected HashSet set = new HashSet();
      
      public void receiveResult(Object o) {
        if (o != null) {
          Object[] a = (Object[]) o;
          for (int i=0; i<a.length; i++)
            set.add(a[i]);
        }
          
        if (i.hasNext()) {
          ((PostClient) i.next()).getContentHashReferences(this);
        } else {
          System.out.println("REFRESHING " + set.size() + " OBJECTS!");
          storage.refreshContentHash((ContentHashReference[]) set.toArray(new ContentHashReference[0]), new ListenerContinuation("Refreshing of objects"));
        }
      }
    };
    
    c.receiveResult(null);
  }
   
  /**
   * This method processes a message to backup the log heads.
   *
   * @param message The incoming message.
   */
  private void processBackupMessage(BackupMessage message) {
    storage.setAggregate(log, new ListenerContinuation("Setting of Aggregate Head") {
      public void receiveResult(Object o) {
        final Iterator j = clients.iterator();
        final HashSet set = new HashSet();
        set.add(log);
        
        Continuation d = new ListenerContinuation("Retrieval of Mutable Data") {
          public void receiveResult(Object o) {
            if (o != null) {
              Object[] a = (Object[]) o;
              for (int i=0; i<a.length; i++)
                set.add(a[i]);
            }
            
            if (j.hasNext()) 
              ((PostClient) j.next()).getLogs(this);
            else 
              storage.backupLogs(log, (Log[]) set.toArray(new Log[0]), new ListenerContinuation("Backing up of mutable objects"));
          }
        };
        
        d.receiveResult(null);
      }
    });
  }
  
  /**
   * This method processes a message to synchronize the pending notification messages.  Also sends out a
   * PublishRequestMessage, if we are not currently processing any deliveries.
   *
   * @param message The incoming message.
   */
  private void processSynchronizeMessage(SynchronizeMessage message) {
    delivery.synchronize();
    
    boolean go = false;
    
    synchronized (deliveryBuffer) {
      go = (deliveryBuffer.size() == 0);
    }
    
    if (go)
      announcePresence();
  }
  
  /**
   * This method processes a notification message by passing it up
   * to the appropriate application.
   *
   * @param message The incoming message.
   */
  private void processEncryptedNotificationMessage(EncryptedNotificationMessage message, Continuation command) {
    logger.fine(endpoint.getId() + ": Encrypted notification message from : " + message.getSender());
    NotificationMessage nm = null;

    // decrypt and verify notification message
    try {
      byte[] key = SecurityUtils.decryptAsymmetric(message.getKey(), keyPair.getPrivate());
      nm = (NotificationMessage) SecurityUtils.deserialize(SecurityUtils.decryptSymmetric(message.getData(), key));
    } catch (Exception e) {
      logger.warning(endpoint.getId() + ": Exception occured which decrypting NotificationMessage " + e + " - dropping on floor.");
      command.receiveException(new PostException("Exception occured which decrypting NotificationMessage " + e + " - dropping on floor."));
      return;
    }

    logger.finer(endpoint.getId() + ": Successfully deserialized notification message from : " + nm.getSender());

    if (! (nm.getDestination().equals(getEntityAddress()))) {
      logger.warning(endpoint.getId() + ": Found ENM at " + getEntityAddress() + " destined for different user " +
                     nm.getDestination() + " - dropping on floor.");
      command.receiveException(new PostException("Found ENM at " + getEntityAddress() + " destined for different user " +
                                                 nm.getDestination() + " - dropping on floor."));
      return;
    }
    
    
    if (! (nm.getSender().equals(message.getSender()))) {
      logger.warning(endpoint.getId() + ": Found ENM from " + message.getSender() + " with internal NM from different sender " +
                     nm.getSender() + " - dropping on floor.");
      command.receiveException(new PostException("Found ENM from " + message.getSender() + " with internal NM from different sender " +
                                                 nm.getSender() + " - dropping on floor."));
      return;
    }

    logger.finer(endpoint.getId() + ": DEBUG: successfully verified ENM with NM: " + nm);

    // deliver notification messag
    PostClient client = (PostClient) clientAddresses.get(nm.getClientAddress());

    if (client != null) {
      client.notificationReceived(nm, command);
    } else {
      logger.warning(endpoint.getId() + ": Found notification message for unknown client " + client + " - dropping on floor.");
      command.receiveException(new PostException("Found notification message for unknown client " + client + " - dropping on floor."));
    }
  }
  
  /**
   * This method handles an incoming group multicast message.
   *
   * @param message The incoming message.
   */
  private void processGroupMessage(GroupNotificationMessage message, Continuation command) {
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
        client.notificationReceived(nm, command);
      } else {
        logger.warning(endpoint.getId() + ": Found notification message for unknown client " + client + " - dropping on floor.");
        command.receiveException(new PostException("Found notification message for unknown client " + client + " - dropping on floor."));
      }
    } catch (Exception e) {
      logger.warning(endpoint.getId() + ": Exception occured while decrypting GroupNotificationMessage " + e + " - dropping on floor.");
      command.receiveException(new PostException("Exception occured while decrypting GroupNotificationMessage " + e + " - dropping on floor."));
    } 
  }
  
  /**
   * Internal method which builds a new PostLog for the log user.  If a previous post log has been specified, 
   * it uses that to clone, otherwise, it simply creates a blank log.  *DO NOT USE!*
   *
   * @param command The command to call once done
   * @return The new post log
   */
  public void createPostLog(Continuation command) {
    if (previousAddress != null) {
      getPostLog(previousAddress, new StandardContinuation(command) {
        public void receiveResult(Object o) {
          PostLog previous = (PostLog) o;
          
          if (previous != null) {
            logger.info(endpoint.getId() + ": Creating new log at " + getEntityAddress() + " based off of address at " + previousAddress);
            
            log = new PostLog(getEntityAddress(), keyPair.getPublic(), certificate, PostImpl.this, previous, parent);
            parent.receiveResult(new Boolean(true));
          } else {
            parent.receiveException(new PostException("Unable to find previous log - aborting!"));
          }
        }
      });
    } else {
      log = new PostLog(getEntityAddress(), keyPair.getPublic(), certificate, this, command);
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
    } else {
      if (postLogs.get(entity) != null) {
        command.receiveResult(postLogs.get(entity));
        return;
      }
    }
    
    synchronized (pendingLogs) {
      if (pendingLogs.get(entity) == null) {
        pendingLogs.put(entity, new Vector());
      } else {
        ((Vector) pendingLogs.get(entity)).add(command);
        return;
      }
    }

    logger.fine(endpoint.getId() + ": Looking up postlog for : " + entity);

    storage.retrieveSigned(new SignedReference(entity.getAddress()), new StandardContinuation(command) {
      public void receiveResult(Object o) {
        final PostLog log = (PostLog) o;

        logger.fine(endpoint.getId() + ": Got response log " + log +  " for entity " + entity);

        
        if (log == null) {
          logger.info(endpoint.getId() + ": Could not find postlog for: " + entity);

          if (entity.equals(getEntityAddress())) {
            if (logRewrite) {
              logger.warning(endpoint.getId() + ": Reinserting log head for entity " + entity);
              createPostLog(new StandardContinuation(parent) {
                public void receiveResult(Object o) {
                  passResult(PostImpl.this.log, parent);
                }
              });
              
              return;
            } else {
              logger.warning(endpoint.getId() + ": Unable to fetch local POST log - aborting");
              passException(new PostException("Unable to locate POST log"), parent);
              return;
            }
          } else {
            logger.warning(endpoint.getId() + ": PostLog lookup for user " + entity + " failed.");
            passResult(null, parent);
            return;
          }
        }

        if ((log.getPublicKey() == null) || (log.getEntityAddress() == null)) {
          passException(new PostException("Malformed PostLog: " + log.getPublicKey() + " " + log.getEntityAddress()), parent);
          return;
        }

        if (! log.getEntityAddress().equals(entity)) {
          passException(new PostException("Wrong PostLog: Asked for PostLog for " + entity + ", got " + log.getEntityAddress()), parent);
          return;
        }
        
        if (! (log.getEntityAddress().equals(log.getCertificate().getAddress()) &&
               log.getPublicKey().equals(log.getCertificate().getKey()))) {
          passException(new PostException("Malformed PostLog: Certificate does not match log owner."), parent);
          return;
        }

        security.verify(log.getCertificate(), new StandardContinuation(parent) {
          public void receiveResult(Object o) {
            if ((new Boolean(true)).equals(o)) {
              storage.verifySigned(log, log.getPublicKey());
              log.setPost(PostImpl.this);

              if (entity.equals(getEntityAddress())) {
                PostImpl.this.log = log;
              } else {
                postLogs.put(entity, log);
              }

              logger.fine(endpoint.getId() + ": Successfully retrieved postlog for: " + entity);

              passResult(log, parent);
            } else {
              logger.warning(endpoint.getId() + ": Ceritficate of PostLog could not be verified for entity " + entity);
              passException(new PostException("Certificate of PostLog could not verified for entity: " + entity), parent);
            }
          }
          
          public void receiveException(Exception e) {
            passException(e, parent);          
          }
        });
      }
      
      public void receiveException(Exception e) {
        if ((e instanceof StorageException) || (e instanceof PastException))
          receiveResult(null);
        else
          passException(e, parent);          
      }
      
      protected void passException(Exception e, Continuation command) {
        command.receiveException(e);
        
        Vector v = null;
        synchronized (pendingLogs) {
          v = (Vector) pendingLogs.remove(entity);
        }
        
        if (v != null)
          for (int i=0; i<v.size(); i++) 
            ((Continuation) v.elementAt(i)).receiveException(e);        
      }
      
      protected void passResult(Object o, Continuation command) {
        command.receiveResult(o);
        
        Vector v = null;
        synchronized (pendingLogs) {
          v = (Vector) pendingLogs.remove(entity);
        }
        
        if (v != null)
          for (int i=0; i<v.size(); i++) 
            ((Continuation) v.elementAt(i)).receiveResult(log);
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

    final PresenceMessage pm = new PresenceMessage(address, endpoint.getLocalNodeHandle());
    endpoint.process(new Executable() {
      public Object execute() {
        return signPostMessage(pm);
      }
    }, new ListenerContinuation("Sending of PresnceMessage") {
      public void receiveResult(Object o) {
        scribe.publish(new Topic(address.getAddress()), new PostScribeMessage((SignedPostMessage) o));
      }
    });
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
   * @param command The command to run once the operation is complete
   */
  public void sendNotification(final NotificationMessage message, Continuation command) {
    final PostUserAddress destination = (PostUserAddress) message.getDestination();

    System.out.println("POST: " + endpoint.getId() + ": Sending notification message " + message + " to: " + destination + " addr: " + destination.getAddress());

    getPostLog(destination, new StandardContinuation(command) {
      public void receiveResult(Object o) {
        PostLog destinationLog = (PostLog) o;

        if (destinationLog == null) {
          logger.warning(endpoint.getId() + ": Could not send notification message to non-existant user " + destination);
          parent.receiveException(new RuntimeException("Could not send notification, because destination user '" + destination + "' could not be found!"));
          return;
        }

        logger.finer(endpoint.getId() + ": Received destination log " + destinationLog);

        try {
          byte[] key = SecurityUtils.generateKeySymmetric();
          byte[] keyCipherText = SecurityUtils.encryptAsymmetric(key, destinationLog.getPublicKey());
          byte[] cipherText = SecurityUtils.encryptSymmetric(SecurityUtils.serialize(message), key);

          EncryptedNotificationMessage enm = new EncryptedNotificationMessage(address, destination, keyCipherText, cipherText);

          delivery.deliver(signPostMessage(enm), new StandardContinuation(parent) {
            public void receiveResult(Object o) {
              parent.receiveResult(Boolean.TRUE);
            }
          });
        } catch (Exception e) {
          logger.warning(endpoint.getId() + ": Exception occured which encrypting NotificationMessage " + e + " - aborting.");
          parent.receiveException(e);
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
   * @param command The command to run once the operation is complete
   */
  public void sendNotificationDirect(final NodeHandle handle, final NotificationMessage message, Continuation command) {
    final PostUserAddress destination = (PostUserAddress) message.getDestination();

    logger.fine(endpoint.getId() + ": Sending notification message " + message + " directly to " + destination + " via " + handle);

    getPostLog(destination, new StandardContinuation(command) {
      public void receiveResult(Object o) {
        PostLog destinationLog = (PostLog) o;

        if (destinationLog == null) {
          logger.warning(endpoint.getId() + ": Could not send notification message to non-existant user " + destination);
          parent.receiveException(new RuntimeException("Could not send notification, because destination user '" + destination + "' could not be found!"));
          return;
        }

        logger.finer(endpoint.getId() + ": Received destination log " + destinationLog);

        try {
          byte[] key = SecurityUtils.generateKeySymmetric();
          byte[] keyCipherText = SecurityUtils.encryptAsymmetric(key, destinationLog.getPublicKey());
          byte[] cipherText = SecurityUtils.encryptSymmetric(SecurityUtils.serialize(message), key);
          EncryptedNotificationMessage enm = new EncryptedNotificationMessage(address, destination, keyCipherText, cipherText);

          logger.finer(endpoint.getId() + ": Sending notification message directly to : " + handle);

          endpoint.route(handle.getId(), new PostPastryMessage(signPostMessage(enm)), handle);
          parent.receiveResult(Boolean.TRUE);
        } catch (Exception e) {
          logger.warning(endpoint.getId() + ": Exception occured which encrypting NotificationMessage " + e + " - dropping on floor.");
          parent.receiveException(e);
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
   * @param command The command to execute once done
   */
  public void sendGroup(NotificationMessage message, Continuation command) {
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
      command.receiveResult(Boolean.TRUE);
    } catch (Exception e) {
      logger.warning(endpoint.getId() + ": Exception occured while encrypting GroupNotificationMessage " + e + " - dropping on floor.");
      command.receiveException(e);
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
  
  public String toString() {
    return "PostImpl[" + address + "]";
  }
}
