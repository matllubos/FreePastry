package rice.past;

import rice.past.messaging.*;

import rice.*;
import rice.pastry.*;
import rice.pastry.client.*;
import rice.pastry.security.*;
import rice.pastry.messaging.*;
import rice.pastry.routing.*;

import rice.storage.*;

import java.io.*;
import java.util.Hashtable;

/**
 * @(#) PASTServiceImpl.java
 *
 * Implementation of PAST, which allows users to store replicated
 * copies of documents on the Pastry network.
 *
 * @version $Id$
 * @author Charles Reis
 */
public class PASTServiceImpl 
  extends PastryAppl
  implements PASTService
{
  /**
   * Whether to print debugging statements.
   */
  public static boolean DEBUG = false;
  
  /**
   * PastryNode this service is running on.
   */
  private final PastryNode _pastryNode;
  
  /**
   * StorageManager used to store objects.
   */
  private final StorageManager _storage;
    
  /**
   * Credentials for this application
   */
  private final Credentials _appCredentials;
  
  /**
   * SendOptions to be used on the Pastry messages.
   */
  private final SendOptions _sendOptions;
  
  /**
   * The table used to store commands waiting for a response.
   * Maps PASTMessageID to Continuation.
   */
  protected final Hashtable _commandTable;
  
  /**
   * Timeout to use while waiting for response messages, in milliseconds.
   */
  private long _timeout = 5000;
  
  
  
  /**
   * Builds a new PASTService to run on the given PastryNode.
   * @param pn PastryNode to run on
   * @param storage StorageManager used to store and retrieve files
   */
  public PASTServiceImpl(PastryNode pn, StorageManager storage) {
    super(pn);
    _pastryNode = pn;
    _storage = storage;
    _appCredentials = new PermissiveCredentials();
    _sendOptions = new SendOptions();
    _commandTable = new Hashtable();
  }
  
  
  /**
   * Returns the StorageManager used by this PAST node.
   */
  public StorageManager getStorage() {
    return _storage;
  }
  
  /**
   * Returns the PastryNode this PAST service is running on.
   */
  public PastryNode getPastryNode() {
    return _pastryNode;
  }
  
  /**
   * Gets the timeout used while waiting for replies.
   */
  public long getTimeout() {
    return _timeout;
  }
  
  /**
   * Sets the timeout used while waiting for replies.
   * @param timeout New value for timeout
   */
  public void setTimeout(long timeout) {
    _timeout = timeout;
  }
  
  // ---------- PastryAppl Methods ----------
  
  /**
   * Returns the address of this application.
   *
   * @return the address.
   */
  public Address getAddress() {
    return PASTAddress.instance();
  }
  
  /**
   * Returns the credentials of this application.
   *
   * @return the credentials.
   */
  public Credentials getCredentials() {
    return _appCredentials;
  }
  
  /**
   * Called by pastry when a message arrives for this application.
   *
   * @param msg the message that is arriving.
   */
  public void messageForAppl(Message msg) {
    if (msg instanceof PASTMessage) {
      PASTMessage pmsg = (PASTMessage) msg;
      if (pmsg.getType() == PASTMessage.REQUEST) {
        // Request
        pmsg.performAction(this);
      }
      else {
        // Response
        _handleResponseMessage(pmsg);
      }
    }
    else {
      System.err.println("PAST Error: Received a non-PAST message:" + msg);
    }
  }
  
  /**
   * Sends a message to a remote PAST node (either a request or response).
   * @param msg PASTMessage to send
   */
  public void sendMessage(PASTMessage msg) {
    if (msg.getType() == PASTMessage.REQUEST) {
      // Request
      routeMsg(msg.getFileId(), msg, _appCredentials, _sendOptions);
    }
    else {
      // Response
      routeMsg(msg.getSource(), msg, _appCredentials, _sendOptions);
    }
  }
  
  /**
   * Sends a request message and stores the given command to be
   * executed when the response is received.
   * @param msg Request to send
   * @param command Command to execute when the result is received
   */
  protected void _sendRequestMessage(PASTMessage msg, 
                                     Continuation command)
  {
    // Update the command table so that this command can collect its
    // response when it arrives.
    _commandTable.put(msg.getID(), command);
    
    // Route the request to the remote node
    sendMessage(msg);
  }
  
  /**
   * Receives a response message after a request has been sent
   * and gives it to the appropriate command.
   */
  protected void _handleResponseMessage(PASTMessage msg) {
    // Look up the command waiting for this response
    Continuation command = 
      (Continuation) _commandTable.get(msg.getID());
        
    if (command != null) {
      // Give response to the command
      command.receiveResult(msg);
    }
    else {
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
  public void insert(NodeId id, Serializable obj, Credentials authorCred,
                     final Continuation command)
  {
    NodeId nodeId = _pastryNode.getNodeId();
    debug("Insert request for file " + id + " at node " + nodeId);
    MessageInsert request = new MessageInsert(nodeId, id, obj, authorCred);
    
    // Send the request
    _sendRequestMessage(request, new Continuation() {
      public void receiveResult(Object result) {
        if (result == null) {
          // Not successful
          command.receiveResult(new Boolean(false));
        }
        else if (result instanceof MessageInsert) {
          // Return whether successful
          boolean success = ((MessageInsert)result).getSuccess();
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
   * Stores an update to the object with the given ID.
   * Asynchronously returns a boolean as the result to the provided
   * UpdateResultCommand, indicating whether the insert was successful.
   * 
   * @param id Pastry key of original object to be updated
   * @param update Persistable update to the original object
   * @param authorCred Update Author's credentials
   * @param command Command to be performed when the result is received
   */
  public void update(NodeId id, Serializable update, Credentials authorCred, 
                     final Continuation command)
  {
    NodeId nodeId = _pastryNode.getNodeId();
    debug("Request to append to file " + id + " at node " + nodeId);
    MessageAppend request = new MessageAppend(nodeId, id, update, authorCred);
    
    // Send the request
    _sendRequestMessage(request, new Continuation() {
      public void receiveResult(Object result) {
        if (result == null) {
          // Not successful
          command.receiveResult(new Boolean(false));
        }
        else if (result instanceof MessageAppend) {
          // Return whether successful
          boolean success = ((MessageAppend)result).getSuccess();
          command.receiveResult(new Boolean(success));
        }
        else {
          // Should have gotten a MessageAppend
          command.receiveException(new IllegalArgumentException("Expected a MessageAppend result, got " + result));
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
  public void lookup(NodeId id, final Continuation command) {
    NodeId nodeId = _pastryNode.getNodeId();
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
  public void exists(NodeId id, final Continuation command) {
    NodeId nodeId = _pastryNode.getNodeId();
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
  public void delete(NodeId id, Credentials authorCred,
                     final Continuation command)
  {
    NodeId nodeId = _pastryNode.getNodeId();
    System.out.println("Deleting the file with ID: " + id);
    MessageReclaim request = 
      new MessageReclaim(_pastryNode.getNodeId(), id, authorCred);
    
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
   * Prints a debugging message to System.out if the
   * DEBUG flag is turned on.
   */
  protected void debug(String message) {
    if (DEBUG) {
      System.out.println("PASTService:  " + message);
    }
  }
}