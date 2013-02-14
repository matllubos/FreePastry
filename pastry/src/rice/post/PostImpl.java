/*******************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate

Copyright 2002-2007, Rice University. Copyright 2006-2007, Max Planck Institute 
for Software Systems.  All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither the name of Rice  University (RICE), Max Planck Institute for Software 
Systems (MPI-SWS) nor the names of its contributors may be used to endorse or 
promote products derived from this software without specific prior written 
permission.

This software is provided by RICE, MPI-SWS and the contributors on an "as is" 
basis, without any representations or warranties of any kind, express or implied 
including, but not limited to, representations or warranties of 
non-infringement, merchantability or fitness for a particular purpose. In no 
event shall RICE, MPI-SWS or contributors be liable for any direct, indirect, 
incidental, special, exemplary, or consequential damages (including, but not 
limited to, procurement of substitute goods or services; loss of use, data, or 
profits; or business interruption) however caused and on any theory of 
liability, whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even if 
advised of the possibility of such damage.

*******************************************************************************/ 
package rice.post;

import java.io.*; 
import java.security.*;
import java.util.*;

import rice.*;
import rice.Continuation.*;

import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.p2p.commonapi.*;
import rice.p2p.commonapi.rawserialization.*;
import rice.p2p.past.*;
import rice.p2p.past.rawserialization.PastContentDeserializer;
import rice.p2p.scribe.*;
import rice.p2p.scribe.rawserialization.ScribeContentDeserializer;
import rice.p2p.util.*;
import rice.p2p.util.rawserialization.*;

import rice.pastry.messaging.JavaSerializedDeserializer;
import rice.post.delivery.*;
import rice.post.log.*;
import rice.post.messaging.*;
import rice.post.rawserialization.*;
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
@SuppressWarnings("unchecked")
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
   * Whether POST should automatically send presence announcements
   */
  private boolean announce;
  
  /**
   * The previous address of the user, only used when re-inserting the new log
   */
  private PostEntityAddress previousAddress;
  
  /**
   * The Environment.
   */
  protected Environment environment;

  protected Logger logger;
  
  /**
   * The instance.
   */
  protected final String instance;
  
  protected NotificationMessageDeserializer notificationMessageDeserializer;
  
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
                  boolean announce,
                  PostEntityAddress previousAddress,
                  long synchronizeInterval,
                  long refreshInterval,
                  long timeoutInterval) throws PostException 
  {
    this.environment = node.getEnvironment();
    this.logger = environment.getLogManager().getLogger(PostImpl.class, instance);

    this.instance = instance;
    this.endpoint = node.buildEndpoint(this, instance);
    this.endpoint.setDeserializer(new rice.p2p.util.rawserialization.JavaSerializedDeserializer(endpoint) {
    
      public Message deserialize(InputBuffer buf, short type, int priority,
          NodeHandle sender) throws IOException {
        switch(type) {
          case BackupMessage.TYPE:
            return new BackupMessage();
          case PostPastryMessage.TYPE:
            return new PostPastryMessage(buf, endpoint);            
          case SynchronizeMessage.TYPE:
            return new SynchronizeMessage();
        }
        return super.deserialize(buf, type, priority, sender);
      }    
    });
    
//    final PostMessageDeserializer postMessageDeserializer = null;
//    PastContentDeserializer deliveryDeserializer = new PastContentDeserializer() {      
//      public PastContent deserializePastContent(InputBuffer buf, Endpoint endpoint,
//          short contentType) throws IOException {
//        switch(contentType) {
//          case Delivery.TYPE:
//            return new Delivery(buf,endpoint,postMessageDeserializer);
//        }
//        throw new IllegalArgumentException("Unknown type:"+contentType);
//      }    
//    };    
    
    this.address = address;
    this.keyPair = keyPair;
    this.certificate = certificate;
    this.caPublicKey = caPublicKey;
    this.logRewrite = logRewrite;
    this.announce = announce;
    this.previousAddress = previousAddress;
    
    this.scribe = new ScribeImpl(node, instance);
    this.scribe.setContentDeserializer(new ScribeContentDeserializer() {
    
      public ScribeContent deserializeScribeContent(InputBuffer buf,
          Endpoint endpoint, short contentType) throws IOException {
        switch(contentType) {
          case PostScribeMessage.TYPE:
            return new PostScribeMessage(buf, endpoint);
        }
        throw new IllegalArgumentException("Unknown type:"+buf);
      }    
    });
    
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
    
    endpoint.register();
    
    endpoint.scheduleMessage(new SynchronizeMessage(), SYNCHRONIZE_WAIT + environment.getRandomSource().nextInt((int) synchronizeInterval), synchronizeInterval);
    endpoint.scheduleMessage(new RefreshMessage(), environment.getRandomSource().nextInt((int) refreshInterval), refreshInterval);
    endpoint.scheduleMessage(new BackupMessage(), environment.getRandomSource().nextInt((int) BACKUP_INTERVAL), BACKUP_INTERVAL);
    
    if (logger.level <= Logger.FINE) logger.log("Constructed new Post with user " + address + " and instance " + instance);
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
    if (logger.level <= Logger.FINEST) logger.log("Received message " + message + " with target " + id);
    if (message instanceof SignedPostMessageWrapper) {
      if (((SignedPostMessageWrapper) message).getMessage().getMessage() instanceof DeliveryMessage)
        processDeliveryMessage((DeliveryMessage) ((SignedPostMessageWrapper) message).getMessage().getMessage(), 
                               new ListenerContinuation("Processing of Pastry Delivery POST Message", environment));
      else  
        processSignedPostMessage(((SignedPostMessageWrapper) message).getMessage(), new ListenerContinuation("Processing of Pastry POST Message", environment));
    } else if (message instanceof SynchronizeMessage) {
      processSynchronizeMessage((SynchronizeMessage) message);
    } else if (message instanceof RefreshMessage) {
      processRefreshMessage((RefreshMessage) message);
    } else if (message instanceof BackupMessage) {
      processBackupMessage((BackupMessage) message);
    } else {
      if (logger.level <= Logger.WARNING) logger.logException("Found unknown message " + message + " - dropping on floor.", new Exception("Stack Trace"));
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
    if (logger.level <= Logger.FINEST) logger.log("Received scribe content " + content + " for topic " + topic);
    if (content instanceof SignedPostMessageWrapper) {
      processSignedPostMessage(((SignedPostMessageWrapper) content).getMessage(), new ListenerContinuation("Processing of Scribe POST message", environment));
    } else {
      if (logger.level <= Logger.WARNING) logger.log("Found unknown Scribe message " + content + " - dropping on floor.");
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
    if (logger.level <= Logger.FINER) logger.log("Processing signed message " + signedMessage + " from sender " + sender + " with address " + sender.getAddress());
    
    getPostLog(sender, new StandardContinuation(command) {
      public void receiveResult(Object o) {
        PostLog senderLog = (PostLog) o;

        // look up sender
        if (senderLog == null) {
          if (logger.level <= Logger.WARNING) logger.log("Found PostMessage from non-existent sender " + sender + " - dropping on floor.");
          parent.receiveException(new PostException("Found PostMessage from non-existent sender " + sender + " - dropping on floor."));
          return;
        }
        
        PostMessage message = signedMessage.getMessage();

        // verify message is signed
        if (! verifySignedPostMessage(signedMessage, senderLog.getPublicKey())) {
          if (logger.level <= Logger.WARNING) logger.log("Problem encountered verifying " + message.getClass().getName() + " from " + sender + " - dropping on floor.");
          parent.receiveException(new PostException("Problem encountered verifying " + message.getClass().getName() + " from " + sender + " - dropping on floor. (senderkey: " + senderLog.getPublicKey() + ")"));
          return;
        }

        if (message instanceof PresenceMessage) {
          processPresenceMessage((PresenceMessage) message, parent);
        } else if (message instanceof EncryptedNotificationMessage) {
          processEncryptedNotificationMessage((EncryptedNotificationMessage) message, parent);
        } else if (message instanceof GroupNotificationMessage) {
          processGroupMessage((GroupNotificationMessage) message, parent);
        } else {
          if (logger.level <= Logger.WARNING) logger.log("Found unknown Postmessage " + message + " - dropping on floor.");
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
    if (logger.level <= Logger.FINE) logger.log("Presence message from : " + message.getSender() + " at " + message.getHandle());

    delivery.presence(message, new StandardContinuation(command) {
      public void receiveResult(Object o) {
        if (o != null) {
          Delivery d = (Delivery)o;
          DeliveryMessage dm = new DeliveryMessage(address, message.getSender(), d.getId(), d.getSignedMessage());
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
    if (logger.level <= Logger.FINE) logger.log("Delivery message from : " + message.getSender());
    if (logger.level <= Logger.FINEST) logger.log("Delivery message contains: "+message.getEncryptedMessage());
    
    if (! message.getDestination().equals(address)) {
      if (logger.level <= Logger.FINER) logger.log("Incorrectly received delivery message at "  + address + " for " + message.getDestination());
      command.receiveResult(new Boolean(false));
      return;
    }
    
    Runnable buffered = new Runnable() {
      // this determines whether there's something to run and if so proceeds
      // makes sure that we process everything, and that we only run once
      // see below
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
        if (message.getId() == null) {
          if (logger.level <= Logger.FINE) logger.log("Got old-style delivery message - filling in Id; value may be bogus");
          message.setId(delivery.getIdForMessage(message.getEncryptedMessage()));
        }
        delivery.check(message.getId(), new StandardContinuation(command) {
          public void receiveResult(Object o) {
            if (((Boolean) o).booleanValue()) {
              if (logger.level <= Logger.FINE) logger.log("Haven't seen message " + message + " before - accepting");
              
              processSignedPostMessage(message.getEncryptedMessage(), new StandardContinuation(parent) {
                public void receiveResult(Object o) {
                  if (o.equals(Boolean.TRUE)) {
                    // the SignedPostMessage has someone else's signature in it
                    // we need to generate our signature of the message
                    byte[] sig = signPostMessage(message.getEncryptedMessage().getMessage()).getSignature();
                    delivery.delivered(message.getEncryptedMessage(), message.getId(), sig, new StandardContinuation(parent) {
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
                    if (logger.level <= Logger.INFO) logger.log("Was told not to accept Notification message " + message.getEncryptedMessage() + " - skipping (val " + o + ")");
                    next();
                  }
                }
                
                public void receiveException(final Exception e) {
                  if (e instanceof PostException) {
                    if (logger.level <= Logger.WARNING) logger.log("Marking message " + message + " as undeliverable due to exception " + e);
                    delivery.undeliverable(message.getEncryptedMessage(), message.getId(), new StandardContinuation(parent) {
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
                    if (logger.level <= Logger.WARNING) logger.log("Received exception " + e + " processing delivery " + message + " - ignoring.");
                    parent.receiveException(e);
                    next();
                  }
                }
              });
            } else {
              if (logger.level <= Logger.FINE) logger.log("Seen message " + message + " before - ignoring");
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

    // make sure that the buffered.run() method is only called once; see above
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
    if (logger.level <= Logger.INFO) logger.log("BEGINNING REFRESH!");
    
    Continuation c = new ListenerContinuation("Retrieval of ContentHashReferences", environment) {
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
          if (logger.level <= Logger.INFO) logger.log("REFRESHING " + set.size() + " OBJECTS!");
          storage.refreshContentHash((ContentHashReference[]) set.toArray(new ContentHashReference[0]), 
              new ListenerContinuation("Refreshing of objects", environment));
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
    storage.setAggregate(log, new ListenerContinuation("Setting of Aggregate Head", environment) {
      public void receiveResult(Object o) {
        final Iterator j = clients.iterator();
        final HashSet set = new HashSet();
        set.add(log);
        
        Continuation d = new ListenerContinuation("Retrieval of Mutable Data", environment) {
          public void receiveResult(Object o) {
            if (o != null) {
              Object[] a = (Object[]) o;
              for (int i=0; i<a.length; i++)
                set.add(a[i]);
            }
            
            if (j.hasNext()) 
              ((PostClient) j.next()).getLogs(this);
            else 
              storage.backupLogs(log, (Log[]) set.toArray(new Log[0]), new ListenerContinuation("Backing up of mutable objects", environment));
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
    
    if (go && announce)
      announcePresence();
  }
  
  /**
   * This method processes a notification message by passing it up
   * to the appropriate application.
   *
   * @param message The incoming message.
   */
  private void processEncryptedNotificationMessage(EncryptedNotificationMessage message, Continuation command) {
    if (logger.level <= Logger.FINE) logger.log("Encrypted notification message from : " + message.getSender());
    NotificationMessage nm = null;

    // decrypt and verify notification message
    try {
      byte[] key = SecurityUtils.decryptAsymmetric(message.getKey(), keyPair.getPrivate());
      byte[] plaintext = SecurityUtils.decryptSymmetric(message.getData(), key);
      if (plaintext[0] == 0x1F && plaintext[1] == 0x8B) {
        nm = (NotificationMessage) SecurityUtils.deserialize(SecurityUtils.decryptSymmetric(message.getData(), key));
      } else {                
        System.out.println("PostImpl.processEncryptedNotificatonMessage():Head of Plaintext:"+plaintext[0]+","+plaintext[1]+","+plaintext[2]+","+plaintext[3]+","+plaintext[4]+","+plaintext[5]+","+plaintext[6]+","+plaintext[7]);
        InputBuffer buf = new SimpleInputBuffer(plaintext);     
        nm = notificationMessageDeserializer.deserializeNotificationMessage(buf, endpoint, buf.readShort());
      }
    } catch (Exception e) {
      if (logger.level <= Logger.WARNING) logger.logException("Exception occured which decrypting NotificationMessage " + e + " - dropping on floor.", e);
      command.receiveException(new PostException("Exception occured whil decrypting NotificationMessage " + e + " - dropping on floor."));
      return;
    }

    if (logger.level <= Logger.FINER) logger.log("Successfully deserialized notification message from : " + nm.getSender());

    if (! (nm.getDestination().equals(getEntityAddress()))) {
      if (logger.level <= Logger.WARNING) logger.log("Found ENM at " + getEntityAddress() + " destined for different user " +
                     nm.getDestination() + " - dropping on floor.");
      command.receiveException(new PostException("Found ENM at " + getEntityAddress() + " destined for different user " +
                                                 nm.getDestination() + " - dropping on floor."));
      return;
    }
    
    
    if (! (nm.getSender().equals(message.getSender()))) {
      if (logger.level <= Logger.WARNING) logger.log("Found ENM from " + message.getSender() + " with internal NM from different sender " +
                     nm.getSender() + " - dropping on floor.");
      command.receiveException(new PostException("Found ENM from " + message.getSender() + " with internal NM from different sender " +
                                                 nm.getSender() + " - dropping on floor."));
      return;
    }

    if (logger.level <= Logger.FINER) logger.log("DEBUG: successfully verified ENM with NM: " + nm);

    // deliver notification messag
    PostClient client = (PostClient) clientAddresses.get(nm.getClientAddress());

    if (client != null) {
      client.notificationReceived(nm, command);
    } else {
      if (logger.level <= Logger.WARNING) logger.log("Found notification message for unknown client " + client + " - dropping on floor.");
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

    if (logger.level <= Logger.FINE) logger.log("Received group message from: " + destination);

    byte[] key = (byte[]) keys.get(destination);

    if (logger.level <= Logger.FINER) logger.log("Using group key " + key + " for decryption.");

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
        if (logger.level <= Logger.WARNING) logger.log("Found notification message for unknown client " + client + " - dropping on floor.");
        command.receiveException(new PostException("Found notification message for unknown client " + client + " - dropping on floor."));
      }
    } catch (Exception e) {
      if (logger.level <= Logger.WARNING) logger.log("Exception occured while decrypting GroupNotificationMessage " + e + " - dropping on floor.");
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
            if (logger.level <= Logger.INFO) logger.log("Creating new log at " + getEntityAddress() + " based off of address at " + previousAddress);
            
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

  public void getAndVerifyPostLog(Continuation command) {
    final PostEntityAddress entity = getEntityAddress();

    if (logger.level <= Logger.FINE) logger.log("Looking up all postlogs for : " + entity);

    storage.retrieveAllSigned(new SignedReference(entity.getAddress()), new StandardContinuation(command) {
      public void receiveResult(Object result) {
        Object[] results = (Object[]) result;

        if (logger.level <= Logger.FINE) logger.log("Got response logs " + result + " for entity " + entity);

        if (result == null) {
          if (logger.level <= Logger.WARNING) logger.log("Unable to fetch local POST log");
          parent.receiveException(new PostException("Unable to locate POST log"));
          return;
        }

        Continuation c = new StandardContinuation(parent) {
          public void receiveResult(Object result) {
            Object[] results = (Object[]) result;
            PostLog goodLog = null;
            for (int i = 0; i < results.length; i++) {
              if (results[i] instanceof PostLog) {
                if (logger.level <= Logger.FINEST) logger.log("Got response log " + results[i] + " for entity " + entity);
                if (goodLog == null)
                  goodLog = (PostLog)results[i];
              } else if (results[i] instanceof Throwable) {
                if (logger.level <= Logger.FINE) logger.logException("Got Exception verifying PostLog ",(Throwable)results[i]);
              } else if (results[i] != null) {
                if (logger.level <= Logger.WARNING) logger.log("Got "+results[i].getClass()+" instead of PostLog verifying postlog for "+entity);
              }
            }
            if (goodLog != null) {
              goodLog.setPost(PostImpl.this);
              PostImpl.this.log = goodLog;
              parent.receiveResult(goodLog);
            } else {
              if (logRewrite) {
                if (logger.level <= Logger.WARNING) logger.log("Reinserting log head for entity " + entity);
                createPostLog(new StandardContinuation(parent) {
                  public void receiveResult(Object o) {
                    parent.receiveResult(PostImpl.this.log);
                  }
                });
              } else {
                parent.receiveException(new PostException("Could not retrieve and verify PostLog - got 0 of " + results.length + " good replicas"));
              }
            }
          }
        };

        MultiContinuation mc = new MultiContinuation(c, results.length);

        for (int i = 0; i < results.length; i++) {
          if (results[i] instanceof PostLog) {
            final PostLog log = (PostLog)results[i];
            Continuation sc = mc.getSubContinuation(i);
  
            if ((log.getPublicKey() == null) || (log.getEntityAddress() == null)) {
              sc.receiveException(new PostException("Malformed PostLog: " + log.getPublicKey() + " " + log.getEntityAddress()));
              continue;
            }
  
            if (!log.getEntityAddress().equals(entity)) {
              sc.receiveException(new PostException("Wrong PostLog: Asked for PostLog for " + entity + ", got " + log.getEntityAddress()));
              continue;
            }
  
            if (!(log.getEntityAddress().equals(log.getCertificate().getAddress()) && log.getPublicKey()
                .equals(log.getCertificate().getKey()))) {
              sc.receiveException(new PostException("Malformed PostLog: Certificate does not match log owner."));
              continue;
            }
  
            if (!keyPair.getPublic().equals(log.getPublicKey())) {
              sc.receiveException(new PostException("Malformed PostLog: key mismatch between replicas."));
              continue;
            }
  
            if (!certificate.equals(log.getCertificate())) {
              sc.receiveResult(new PostException("Malformed PostLog: certificate mismatch between replicas."));
              continue;
            }
  
            security.verify(log.getCertificate(), new StandardContinuation(sc) {
              public void receiveResult(Object o) {
                if (Boolean.TRUE.equals(o)) {
                  if (!storage.verifySigned(log, log.getPublicKey())) {
                    parent.receiveException(new PostException("PostLog could not verified for entity: " + entity));
                    return;
                  }
                  if (logger.level <= Logger.FINE) logger.log("Successfully retrieved postlog for: " + entity);
  
                  parent.receiveResult(log);
                } else {
                  parent.receiveException(new PostException("Certificate of PostLog could not verified for entity: " + entity));
                }
              }
            });
          } else {
            mc.getSubContinuation(i).receiveResult(results[i]);
          }
        }
      }
    });
  }
  
  /**
   * @return The PostLog belonging to the this entity,
   */
  public void getPostLog(Continuation command) {
    getPostLog(getEntityAddress(), command);
  }
  
  /**
   * @return The PostLog belonging to the given entity, eg. to acquire another
   *         user's public key.
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

    if (logger.level <= Logger.FINE) logger.log("Looking up postlog for : " + entity);

    storage.retrieveSigned(new SignedReference(entity.getAddress()), new StandardContinuation(command) {
      public void receiveResult(Object o) {
        final PostLog log = (PostLog) o;

        if (logger.level <= Logger.FINE) logger.log("Got response log " + log +  " for entity " + entity);

        
        if (log == null) {
          if (logger.level <= Logger.INFO) logger.log("Could not find postlog for: " + entity);

          if (entity.equals(getEntityAddress())) {
            if (logRewrite) {
              if (logger.level <= Logger.WARNING) logger.log("Reinserting log head for entity " + entity);
              createPostLog(new StandardContinuation(parent) {
                public void receiveResult(Object o) {
                  passResult(PostImpl.this.log, parent);
                }
              });
              
              return;
            } else {
              if (logger.level <= Logger.WARNING) logger.log("Unable to fetch local POST log - aborting");
              passException(new PostException("Unable to locate POST log"), parent);
              return;
            }
          } else {
            if (logger.level <= Logger.WARNING) logger.log("PostLog lookup for user " + entity + " failed.");
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
              if (!storage.verifySigned(log, log.getPublicKey())) {
                  if (logger.level <= Logger.WARNING) logger.log("PostLog could not be verified for entity " + entity);
                 passException(new PostException("PostLog could not verified for entity: " + entity), parent);
                 return;
              }
              log.setPost(PostImpl.this);

              if (entity.equals(getEntityAddress())) {
                PostImpl.this.log = log;
              } else {
                postLogs.put(entity, log);
              }

              if (logger.level <= Logger.FINE) logger.log("Successfully retrieved postlog for: " + entity);

              passResult(log, parent);
            } else {
              if (logger.level <= Logger.WARNING) logger.log("Ceritficate of PostLog could not be verified for entity " + entity);
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
    if (logger.level <= Logger.FINER) logger.log("Publishing presence to the group " + address.getAddress());

    final PresenceMessage pm = new PresenceMessage(address, endpoint.getLocalNodeHandle());
    endpoint.process(new Executable() {
      public Object execute() {
        return signPostMessage(pm);
      }
    }, new ListenerContinuation("Sending of PresnceMessage", environment) {
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

    if (logger.level <= Logger.FINER) logger.log( "POST: Sending notification message " + message + " to: " + destination + " addr: " + destination.getAddress());

    getPostLog(destination, new StandardContinuation(command) {
      public void receiveResult(Object o) {
        PostLog destinationLog = (PostLog) o;

        if (destinationLog == null) {
          if (logger.level <= Logger.WARNING) logger.log("Could not send notification message to non-existant user " + destination);
          parent.receiveException(new RuntimeException("Could not send notification, because destination user '" + destination + "' could not be found!"));
          return;
        }

        if (logger.level <= Logger.FINER) logger.log("Received destination log " + destinationLog);

        try {
          byte[] key = SecurityUtils.generateKeySymmetric();
          byte[] keyCipherText = SecurityUtils.encryptAsymmetric(key, destinationLog.getPublicKey());
          byte[] cipherText;
          if (message instanceof Raw) {
            SimpleOutputBuffer sob = new SimpleOutputBuffer();
            sob.writeShort(((Raw)message).getType());
            message.serialize(sob);
            byte[] plaintext = sob.getBytes();
//            System.out.println("PostImpl.receiveResult2():Head of Plaintext:"+plaintext[0]+","+plaintext[1]+","+plaintext[2]+","+plaintext[3]+","+plaintext[4]+","+plaintext[5]+","+plaintext[6]+","+plaintext[7]);
            cipherText = SecurityUtils.encryptSymmetric(sob.getBytes(), key, 0, sob.getWritten());
          } else {
            cipherText = SecurityUtils.encryptSymmetric(SecurityUtils.serialize(message), key);            
          }
//          System.out.println("PostImpl.receiveResult2(): Constructing EncryptedNotificationMessage["+message+"]");
          EncryptedNotificationMessage enm = new EncryptedNotificationMessage(address, destination, keyCipherText, cipherText);

          delivery.deliver(signPostMessage(enm), new StandardContinuation(parent) {
            public void receiveResult(Object o) {
              parent.receiveResult(Boolean.TRUE);
            }
          });
        } catch (Exception e) {
          if (logger.level <= Logger.WARNING) logger.log("Exception occured which encrypting NotificationMessage " + e + " - aborting.");
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

    if (logger.level <= Logger.FINE) logger.log("Sending notification message " + message + " directly to " + destination + " via " + handle);

    getPostLog(destination, new StandardContinuation(command) {
      public void receiveResult(Object o) {
        PostLog destinationLog = (PostLog) o;

        if (destinationLog == null) {
          if (logger.level <= Logger.WARNING) logger.log("Could not send notification message to non-existant user " + destination);
          parent.receiveException(new RuntimeException("Could not send notification, because destination user '" + destination + "' could not be found!"));
          return;
        }

        if (logger.level <= Logger.FINER) logger.log("Received destination log " + destinationLog);

        try {
          byte[] key = SecurityUtils.generateKeySymmetric();
          byte[] keyCipherText = SecurityUtils.encryptAsymmetric(key, destinationLog.getPublicKey());
          byte[] cipherText;// = SecurityUtils.encryptSymmetric(SecurityUtils.serialize(message), key);
          if (message instanceof Raw) {
            SimpleOutputBuffer sob = new SimpleOutputBuffer();
            sob.writeShort(((Raw)message).getType());
            message.serialize(sob);
            byte[] plaintext = sob.getBytes();
//            System.out.println("PostImpl.receiveResult1():Head of Plaintext:"+plaintext[0]+","+plaintext[1]+","+plaintext[2]+","+plaintext[3]+","+plaintext[4]+","+plaintext[5]+","+plaintext[6]+","+plaintext[7]);

            cipherText = SecurityUtils.encryptSymmetric(plaintext, key, 0, sob.getWritten());
          } else {
            cipherText = SecurityUtils.encryptSymmetric(SecurityUtils.serialize(message), key);            
          }
//          System.out.println("PostImpl.receiveResult(): Constructing EncryptedNotificationMessage["+message+"]");
          EncryptedNotificationMessage enm = new EncryptedNotificationMessage(address, destination, keyCipherText, cipherText);

          if (logger.level <= Logger.FINER) logger.log("Sending notification message directly to : " + handle);

          endpoint.route(handle.getId(), new PostPastryMessage(signPostMessage(enm)), handle);
          parent.receiveResult(Boolean.TRUE);
        } catch (Exception e) {
          if (logger.level <= Logger.WARNING) logger.log("Exception occured which encrypting NotificationMessage " + e + " - dropping on floor.");
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

    if (logger.level <= Logger.FINE) logger.log("Sending message " + message + " to group " + destination + " using key " + key);
    
    try {
      byte[] cipherText = null;
      byte[] plainText;
      int plainTextLength = 0;
      if (message instanceof Raw) {
        SimpleOutputBuffer sob = new SimpleOutputBuffer();
        sob.writeShort(((Raw)message).getType());
        message.serialize(sob);
        byte[] plaintext = sob.getBytes();
//        System.out.println("PostImpl.sendGroup():Head of Plaintext:"+plaintext[0]+","+plaintext[1]+","+plaintext[2]+","+plaintext[3]+","+plaintext[4]+","+plaintext[5]+","+plaintext[6]+","+plaintext[7]);        
        plainText = sob.getBytes();
        plainTextLength = sob.getWritten();
      } else {
        plainText = SecurityUtils.serialize(message);
        plainTextLength = plainText.length;
      }
      
      if (key != null) {        
        cipherText = SecurityUtils.encryptSymmetric(plainText, key, 0, plainTextLength);
      } else {
        cipherText = new byte[plainTextLength];
        System.arraycopy(plainText, 0, cipherText, 0, plainTextLength);
      }

      GroupNotificationMessage gnm = new GroupNotificationMessage(address, destination, cipherText);

      if (logger.level <= Logger.FINER) logger.log("Built encrypted notfn msg " + gnm + " for destination " + destination);

      scribe.publish(new Topic(destination.getAddress()), new PostScribeMessage(signPostMessage(gnm)));
      command.receiveResult(Boolean.TRUE);
    } catch (Exception e) {
      if (logger.level <= Logger.WARNING) logger.log("Exception occured while encrypting GroupNotificationMessage " + e + " - dropping on floor.");
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
      return new SignedPostMessage(message, keyPair.getPrivate());
    } catch (SecurityException e) {
      if (logger.level <= Logger.WARNING) logger.logException("SecurityException " + e + " occured while siging PostMessage " + message + " - aborting.",e);
      return null;
    } catch (IOException e) {
      if (logger.level <= Logger.WARNING) logger.logException("IOException " + e + " occured while siging PostMessage " + message + " - aborting.",e);
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
        if (logger.level <= Logger.WARNING) logger.log("Cannot verify PostMessage with null key!" + message + " " + key);
        return false;
      } 
      
      return message.verify(key);
    } catch (SecurityException e) {
      if (logger.level <= Logger.WARNING) logger.logException("SecurityException " + e + " occured while verifiying PostMessage " + message + " - aborting.",e);
      return false;
    }
  }
  
  public String toString() {
    return "PostImpl[" + address + "]";
  }

  public Environment getEnvironment() {
    return environment;
  }
  
  /**
   * @return the instance of post
   */
  public String getInstance() {
    return instance;
  }

  public NotificationMessageDeserializer getNotificationMessageDeserializer() {
    return notificationMessageDeserializer;
  }

  public void setNotificationMessageDeserializer(
      NotificationMessageDeserializer notificationMessageDeserializer) {
    this.notificationMessageDeserializer = notificationMessageDeserializer;
  }
}
