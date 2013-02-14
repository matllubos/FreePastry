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
package rice.post.delivery;

import java.util.*;
import java.math.*;
import java.io.*;

import rice.*;
import rice.Continuation.*;
import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.p2p.commonapi.*;
import rice.p2p.commonapi.rawserialization.InputBuffer;
import rice.p2p.past.*;
import rice.p2p.past.gc.*;
import rice.p2p.past.rawserialization.PastContentDeserializer;
import rice.p2p.scribe.*;

import rice.post.*;
import rice.post.messaging.*;
import rice.post.security.*;

/**
 * This class encapsulates the logic for the delivery of notification messages
 * to users in the Post system.
 * 
 * @version $Id$
 */
@SuppressWarnings("unchecked")
public class DeliveryService implements ScribeClient {
  
  /**
   * The default timeout for delivery requests and receipts
   */
  protected long timeoutInterval;

  /**
   * The address of the user running this storage service.
   */
  protected PostImpl post;
  
  /**
   * The PAST service used for storing pending notifications
   */
  protected DeliveryPast pending;
  
  /**
   * The PAST service used for storing delivery receipts
   */
  protected Past delivered;
  
  /**
   * The Scribe used for subscribing to pending groups
   */
  protected Scribe scribe;
  
  /**
   * The factory used for creating ids
   */
  protected IdFactory factory;
  
  /**
   * A cache of recently-received delivery message ids
   */
  protected HashSet cache;
  
  protected Environment environment;
  protected Logger logger;
  
  /**
   * Contructs a StorageService given a PAST to run on top of.
   *
   * @param past The PAST service to use.
   * @param credentials Credentials to use to store data.
   * @param keyPair The keypair to sign/verify data with
   */
  public DeliveryService(PostImpl post, DeliveryPast pending, Past delivered, Scribe scribe, IdFactory factory, long timeoutInterval) {
    this.environment = post.getEnvironment();
    this.post = post;
    this.logger = post.getEnvironment().getLogManager().getLogger(DeliveryService.class, post.getInstance());
    this.pending = pending;

    PastContentDeserializer pcd = new PastContentDeserializer() {
      
      public PastContent deserializePastContent(InputBuffer buf, Endpoint endpoint,
          short contentType) throws IOException {
        switch(contentType) {
          case Delivery.TYPE:
            return new Delivery(buf, endpoint);
          case Receipt.TYPE:
            return new Receipt(buf, endpoint);
          case Undeliverable.TYPE:
            return new Undeliverable(buf, endpoint);
        }
        throw new IllegalArgumentException("Unknown type:"+contentType);
      }    
    };
    pending.setContentDeserializer(pcd);
    
    this.delivered = delivered;
    delivered.setContentDeserializer(pcd);
    this.scribe = scribe;
    this.factory = factory;
    this.timeoutInterval = timeoutInterval;
    this.cache = new HashSet();
  }
  
  /**
   * Internal method which returns what the timeout should be for an
   * object inserted now.  Basically, does Systemm.currentTimeMillis() +
   * timeoutInterval.
   *
   * @return The default timeout period for an object inserted now
   */
  protected long getTimeout() {
    return environment.getTimeSource().currentTimeMillis() + timeoutInterval;
  }
  
  /**
   * Requests delivery of the given EncryptedNotificationMessage, which internally
   * contains the destination user.  This method inserts the object into Past, and
   * the replicas will automatically pick up the message.
   *
   * @param message The message to deliver
   * @param command The command to run once finished
   */
  public void deliver(SignedPostMessage message, Continuation command) {
    if (logger.level <= Logger.FINER) logger.log( post.getEndpoint().getId() + ": Delivering message " + message);    
    pending.insert(new Delivery(message, factory), getTimeout(), command);
  }
  
  /** 
   * Is called when a presence message is received.  This method causes the
   * delivery service to send the first ENM to the assoicated user.
   *
   * @param message The presence message that was received
   * @param command The command to call with the message to send, if there is one
   */
  public void presence(PresenceMessage message, Continuation command) {
    if (logger.level <= Logger.FINER) logger.log( "Responding to presence message " + message);
    
    pending.getMessage(message.getSender(), new StandardContinuation(command) {
      public void receiveResult(Object o) {
        parent.receiveResult((o == null ? null : ((Delivery) o)));
      }
    });
  }
  
  public Id getIdForMessage(SignedPostMessage message) {
    return (new Delivery(message, factory)).getId();
  }
  
  /**
   * Determines whether or not the given ENM has been delivered before
   *
   * @param id The Id of the message in question
   * @param command The command to run once finished
   */
  public void check(Id id, Continuation command) {
    if (logger.level <= Logger.FINER) logger.log( "Checking for existence of message with id " + id);
    if (cache.contains(id)) {
      command.receiveResult(new Boolean(false));
    } else {
      delivered.lookup(id, new StandardContinuation(command) {
        public void receiveResult(Object o) {
          parent.receiveResult(new Boolean(o == null));
        }
      });
    }
  }
  
  /**
   * Records delivery of the given message to the user.  THis method inserts the receipt
   * and the replicas holding the corresponding delivery request will notice the receipt 
   * and disregard the request.
   *
   * @param message The message that was delivered
   * @param signature The signature
   * @param command The command to run once finished
   */
  public void delivered(SignedPostMessage message, Id id, byte[] signature, Continuation command) { 
    if (logger.level <= Logger.FINER) logger.log( "Inserting receipt for " + message);
    final Receipt receipt = new Receipt(message, id, signature);
    
    cache.add(receipt.getId());
    
    if (delivered instanceof GCPast) {
      ((GCPast) delivered).insert(receipt, getTimeout(), new ErrorContinuation(command) {
        public void receiveException(Exception e) {
          cache.remove(receipt.getId());
          parent.receiveException(e);
        }
      });
    } else {
      delivered.insert(receipt, new ErrorContinuation(command) {
        public void receiveException(Exception e) {
          cache.remove(receipt.getId());
          parent.receiveException(e);
        }
      });
    }
  }
  
  /**
   * Records a message as being undeliverable, which will ensure that delivery won't be attempted
   * again, but does not provide a receipt.
   *
   * @param message The message that was delivered
   * @param command The command to run once finished
   */
  public void undeliverable(SignedPostMessage message, Id id, Continuation command) { 
    if (logger.level <= Logger.FINER) logger.log( "Inserting undeliverable for " + message);
    final Undeliverable receipt = new Undeliverable(message, id);
    
    cache.add(receipt.getId());
    
    if (delivered instanceof GCPast) {
      ((GCPast) delivered).insert(receipt, getTimeout(), new ErrorContinuation(command) {
        public void receiveException(Exception e) {
          cache.remove(receipt.getId());
          parent.receiveException(e);
        }
      });
    } else {
      delivered.insert(receipt, new ErrorContinuation(command) {
        public void receiveException(Exception e) {
          cache.remove(receipt.getId());
          parent.receiveException(e);
        }
      });
    }
  }
  
  /**
   * Method which periodically checks to see if we've got receipts for
   * any outstanding messages.  If so, then we remove the outstanding message
   * from our pending list.  Also, this method makes sure we are subscribed for
   * the correct Scribe groups by looking at the messages we are responsible for.
   */
  public void synchronize() { 
    pending.synchronize(new ListenerContinuation("Synchronization of Delivery Service", environment) {
      public void receiveResult(Object o) {
        pending.getGroups(new StandardContinuation(this) {
          public void receiveResult(Object o) {
            PostEntityAddress[] addresses = (PostEntityAddress[]) o;
            
            for (int i=0; i<addresses.length; i++) 
              scribe.subscribe(new Topic(addresses[i].getAddress()), DeliveryService.this, null);
            
            Topic[] topics = scribe.getTopics(DeliveryService.this);
            
            for (int i=0; i<topics.length; i++) {
              boolean found = false;
              
              for (int j=0; j<addresses.length && !found; j++) {
                if (addresses[j].getAddress().equals(topics[i].getId()))
                  found = true;
              }
              
              if (! found) 
                scribe.unsubscribe(topics[i], DeliveryService.this);
            }
          }
        });
      }
    });
  }
  
  /**
    * Method by which Scribe delivers a message to this client.
   *
   * @param msg The incoming message.
   */
  public void deliver(Topic topic, ScribeContent content) {
    post.deliver(topic, content);
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
    return post.anycast(topic, content);
  }
  
  /**
    * Informs this client that a child was added to a topic in
   * which it was interested in.
   *
   * @param topic The topic to unsubscribe from
   * @param child The child that was added
   */
  public void childAdded(Topic topic, NodeHandle child) {
    post.childAdded(topic, child);
  }
  
  /**
    * Informs this client that a child was removed from a topic in
   * which it was interested in.
   *
   * @param topic The topic to unsubscribe from
   * @param child The child that was removed
   */
  public void childRemoved(Topic topic, NodeHandle child) {
    post.childRemoved(topic, child);
  }
  
  /**
    * Informs the client that a subscribe on the given topic failed
   * - the client should retry the subscribe or take appropriate
   * action.
   *
   * @param topic The topic which the subscribe failed on
   */
  public void subscribeFailed(Topic topic) {
    post.subscribeFailed(topic);
  }

}
