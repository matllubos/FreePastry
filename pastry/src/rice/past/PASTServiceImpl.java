/*************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate

Copyright 2002, Rice University. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither  the name  of Rice  University (RICE) nor  the names  of its
contributors may be  used to endorse or promote  products derived from
this software without specific prior written permission.

This software is provided by RICE and the contributors on an "as is"
basis, without any representations or warranties of any kind, express
or implied including, but not limited to, representations or
warranties of non-infringement, merchantability or fitness for a
particular purpose. In no event shall RICE or contributors be liable
for any direct, indirect, incidental, special, exemplary, or
consequential damages (including, but not limited to, procurement of
substitute goods or services; loss of use, data, or profits; or
business interruption) however caused and on any theory of liability,
whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even
if advised of the possibility of such damage.

********************************************************************************/

package rice.past;

import rice.past.messaging.*;

import rice.*;
import rice.p2p.commonapi.*;
import rice.rm.*;
import rice.persistence.*;

import java.io.*;
import java.util.*;

/**
 * @(#) PASTServiceImpl.java
 *
 * Implementation of PAST, which allows users to store replicated
 * copies of documents on the Pastry network. <b>This version of Past has been
 * deprecated - please migrate existing applications to the version in rice.p2p.past.</b>
 *
 * @version $Id$
 *
 * @author Charles Reis
 * @author Alan Mislove
 * @author Ansley Post
 *
 * @deprecated This version of PAST has been deprecated - please use the version
 *   located in the rice.p2p.past package.
 */
public class PASTServiceImpl implements PASTService, Application, RMClient {
  
  /**
   * Whether to print debugging statements.
   */
  public static boolean DEBUG = false;
  
  /**
   * Node this service is running on.
   */
  private Node node;

  /**
   * Endpoint which this application uses
   */
  private Endpoint endpoint;
  
  /**
   * Storage used to store objects (persistedly).
   */
  private StorageManager storage;
  
  /**
   * The table used to store commands waiting for a response.
   * Maps PASTMessageID to Continuation.
   */
  protected Hashtable commandTable;
  
  /**
   * Timeout to use while waiting for response messages, in milliseconds.
   */
  private long timeout = 5000;

  /**
   * Replication factor to use with the replication manager 
   * Should probably make this slightly more configurable
   */
  private static int REPLICATION_FACTOR = 4;
 
  /**
   * Replication Manager to use
   */
  private RM replicationManager;
  
  /**
   * Builds a new PASTService to run on the given PastryNode, given a
   * Storage object (to persistedly store objects) and a cache used
   * to cache objects.
   *
   * @param pastry PastryNode to run on
   * @param storage The Storage object to use for storage and caching
   */
  public PASTServiceImpl(Node node, StorageManager storage, String instance) {
    this.storage = storage;
    this.node = node;
    commandTable = new Hashtable();
    this.endpoint = node.registerApplication(this, instance);

    replicationManager = new RMImpl((rice.pastry.PastryNode) node, this, REPLICATION_FACTOR, instance);
  }

  /**
   * Returns the StorageManager object
   *
   * @return This PAST's StorageManager object
   */
  public StorageManager getStorage() {
    return storage;
  }

  /**
   * Returns the Node 
   *
   * @return This PAST's Node
   */
  public Id getId() {
    return endpoint.getId();
  }
  
  // ---------- PastryAppl Methods ----------
  
  /**
   * Called by pastry when a message arrives for this application.
   *
   * @param msg the message that is arriving.
   */
  public void deliver(Id id, Message msg) {
    if (msg instanceof PASTMessage) {
      PASTMessage pmsg = (PASTMessage) msg;
      
      if (pmsg.getType() == PASTMessage.REQUEST) {
        pmsg.performAction(this);
      } else {
        _handleResponseMessage(pmsg);
      }
    } else {
      System.err.println("PAST Error: Received a non-PAST message:" + msg + " - dropping on floor.");
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
   * Sends a message to a remote PAST node (either a request or response).
   *
   * @param msg PASTMessage to send
   */
  public void sendMessage(PASTMessage msg) {
    if (msg.getType() == PASTMessage.REQUEST) {
      endpoint.route(msg.getFileId(), msg, null);
    } else {
      endpoint.route(msg.getSource(), msg, null);
    }
  }
  
  /**
   * Sends a request message and stores the given command to be
   * executed when the response is received.
   * @param msg Request to send
   * @param command Command to execute when the result is received
   */
  protected void _sendRequestMessage(PASTMessage msg, Continuation command) {
    // Update the command table so that this command can collect its
    // response when it arrives.
    commandTable.put(msg.getID(), command);
    
    // Route the request to the remote node
    sendMessage(msg);
  }
  
  /**
   * Receives a response message after a request has been sent
   * and gives it to the appropriate command.
   */
  protected void _handleResponseMessage(PASTMessage msg) {
    // Look up the command waiting for this response
    Continuation command = (Continuation) commandTable.get(msg.getID());
        
    if (command != null) {
      // Give response to the command
      command.receiveResult(msg);
    } else {
      // We don't recognize this response message, so ignore it.
    }
  }
  
  // ---------- PASTService Methods ----------
  
  /**
   * Inserts an object with the given ID into distributed storage.
   * Asynchronously returns a boolean as the result to the provided
   * InsertResultCommand, indicating whether the insert was successful.
   * 
   * @param id Pastry key identifying the object to be stored
   * @param obj Persistable object to be stored
   * @param authorCred Author's credentials
   * @param command Command to be performed when the result is received
   */
  public void insert(Id id, Serializable obj, final Continuation command) {
    replicationManager.registerKey((rice.pastry.Id) id);
    
    Id nodeId = endpoint.getId();
    debug("Insert request for file " + id + " at node " + nodeId);
    MessageInsert request = new MessageInsert(nodeId, id, obj);
    
    // Send the request
    _sendRequestMessage(request, new Continuation() {
      public void receiveResult(Object result) {
        if (result == null) {
          System.out.println("ERROR: Recieved null result in PAST.insert");
          
          // Not successful
          command.receiveResult(new Boolean(false));
        }
        else if (result instanceof MessageInsert) {
          // Return whether successful
          boolean success = ((MessageInsert)result).getSuccess();
          if (! success) {
            System.out.println("ERROR: Recieved bad result in PAST.insert");
          }
          
          command.receiveResult(new Boolean(success));
        }
        else {
          // Should have gotten a MessageInsert
          command.receiveException(new IllegalArgumentException("Expected a MessageInsert result, got " + result));
        }
      }
      public void receiveException(Exception result) {
        command.receiveException(result);
      }
    });
  }
    
  /**
   * Retrieves the object and all associated updates with the given ID.
   * Asynchronously returns a StorageObject as the result to the provided
   * Continuation.
   * 
   * @param id Pastry key of original object
   * @param command Command to be performed when the result is received
   */
  public void lookup(Id id, final Continuation command) {
    Id nodeId = endpoint.getId();
    debug("Request to look up file " + id + " at node " + nodeId);
    MessageLookup request = new MessageLookup(nodeId, id);
    
    // Send the request
    _sendRequestMessage(request, new Continuation() {
      public void receiveResult(Object result) {
        if (result == null) {
          // Null result
          command.receiveResult(null);
        }
        else if (result instanceof MessageLookup) {
          // Return the content discovered
          command.receiveResult(((MessageLookup)result).getContent());
        }
        else {
          // Should have gotten a MessageLookup
          command.receiveException(new IllegalArgumentException("Expected a MessageLookup result, got " + result));
        }
      }
      public void receiveException(Exception result) {
        command.receiveException(result);
      }
    });
  }
  
  /**
   * Determines whether an object is currently stored at the given ID.
   * Asynchronously returns a boolean as the result to the provided
   * Continuation, indicating whether the object exists.
   * 
   * @param id Pastry key of original object
   * @param command Command to be performed when the result is received
   */
  public void exists(Id id, final Continuation command) {
    Id nodeId = endpoint.getId();
    debug("Request to determine if file " + id + " exists, at node " + nodeId);
    MessageExists request = new MessageExists(nodeId, id);
    
    // Send the request
    _sendRequestMessage(request, new Continuation() {
      public void receiveResult(Object result) {
        if (result == null) {
          // Not successful
          command.receiveResult(new Boolean(false));
        }
        else if (result instanceof MessageExists) {
          // Return whether object exists
          boolean exists = ((MessageExists)result).exists();
          command.receiveResult(new Boolean(exists));
        }
        else {
          // Should have gotten a MessageExists
          command.receiveException(new IllegalArgumentException("Expected a MessageExists result, got " + result));
        }
      }
      public void receiveException(Exception result) {
        command.receiveException(result);
      }
    });
  }
 
  /**
   * Reclaims the storage used by the object with the given ID.
   * Asynchronously returns a boolean as the result to the provided
   * Continuation, indicating whether the delete was successful.
   * 
   * @param id Pastry key of original object
   * @param authorCred Author's credentials
   */
  public void delete(Id id, final Continuation command) {
    Id nodeId = endpoint.getId();
    System.out.println("Deleting the file with ID: " + id);
    MessageReclaim request = new MessageReclaim(nodeId, id);
    
    // Send the request
    _sendRequestMessage(request, new Continuation() {
      public void receiveResult(Object result) {
        if (result == null) {
          // Not successful
          command.receiveResult(new Boolean(false));
        }
        else if (result instanceof MessageReclaim) {
          // Return whether successful
          boolean success = ((MessageReclaim)result).getSuccess();
          command.receiveResult(new Boolean(success));
        }
        else {
          // Should have gotten a MessageReclaim
          command.receiveException(new IllegalArgumentException("Expected a MessageReclaim result, got " + result));
        }
      }
      public void receiveException(Exception result) {
        command.receiveException(result);
      }
    });
  }

  /**
   * Fetchs the given object and inserts it into the storage.  This method is designed
   * to be called by PAST itself in order to get objects which it is a replica for.
   *
   * @param id Pastry key of original object
   */
  private void fetch(final Id id, final Continuation command) {
    Id nodeId = endpoint.getId();
    debug("Request to fetch up file " + id + " at node " + nodeId);
    MessageFetch request = new MessageFetch(nodeId, id);

    // Send the request
    _sendRequestMessage(request, new Continuation() {
      public void receiveResult(final Object result) {
        storage.store((rice.pastry.Id) id, ((MessageFetch)result).getContent(), command);
      }

      public void receiveException(Exception result) {
        System.out.println("ERROR - Exception " + result + " occurred during fetching.");
      }
    });
  }

 // ---------- RMClient Methods ----------

  /* This upcall is used by the Replica Manager to get the
    * replica factor to associate with itself.
    */
  public int getReplicaFactor() {
    return REPLICATION_FACTOR;
  }

  /**
   * This upcall is invoked to notify the application that is should
   * fetch the cooresponding keys in this set, since the node is now
   * responsible for these keys also
   */
  public void fetch(rice.pastry.IdSet keySet) {
    final Iterator i = keySet.getIterator();

    Continuation c = new Continuation() {
      public void receiveResult(Object o) {
        if (! o.equals(new Boolean(true))) {
          System.out.println("Storage of object failed!");
        }

        if (i.hasNext()) {
          fetch((Id) i.next(), this);
        }
      }

      public void receiveException(Exception e) {
        System.out.println("ERROR - Exception " + e + " occurred during storage of fetched object ");
      }
    };

    c.receiveResult(new Boolean(true));
  }


  /**
   * This upcall is simply to denote that the underlying replica manager
   * (rm) is ready.
   */
  public void rmIsReady(RM rm) {
  }

  /**
   * This upcall is to notify the application of the range of keys for
   * which it is responsible. The application might choose to react to
   * call by calling a scan(complement of this range) to the persistance
   * manager and get the keys for which it is not responsible and
   * call delete on the persistance manager for those objects
   */
  public void isResponsible(rice.pastry.IdRange range) {
    IdRange notRange = range.getComplementRange();

    Continuation c = new Continuation() {
      private Iterator notIds;
      
      public void receiveResult(Object o) {
        if (o instanceof IdSet) {
          notIds = ((IdSet) o).getIterator();
        } else if (! o.equals(new Boolean(true))) {
          System.out.println("Unstore of Id did not succeed!");
        }

        if (notIds.hasNext()) {
          storage.unstore((rice.pastry.Id) notIds.next(), this);
        }
      }

      public void receiveException(Exception e) {
        System.out.println("Exception " + e + " occurred during removal of objects.");
      }
    };

    storage.getStorage().scan((rice.pastry.IdRange) notRange, c);
  }

  /**
   * This upcall should return the set of keys that the application
   * currently stores in this range. Should return a empty IdSet (not null), in
   * the case that no keys belong to this range
   */
  public rice.pastry.IdSet scan(rice.pastry.IdRange range) {
    return (rice.pastry.IdSet) storage.getStorage().scan(range);
  }

  // ---------- Debug Methods ---------------
  
  /**
   * Prints a debugging message to System.out if the
   * DEBUG flag is turned on.
   */
  protected void debug(String message) {
    if (DEBUG) {
      System.out.println("PASTService:  " + message);
    }
  }
}
