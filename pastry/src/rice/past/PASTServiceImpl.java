package rice.past;

import rice.past.messaging.*;

import rice.pastry.*;
import rice.pastry.client.*;
import rice.pastry.security.*;
import rice.pastry.messaging.*;
import rice.pastry.routing.*;

import rice.storage.*;

import ObjectWeb.Persistence.*;

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
   * PersistenceManager used to store objects.
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
   * Replication factor.  Indicates how many nodes
   * each document is stored on.
   */
  private int _k;
  
  /**
   * The table used to store information for blocked threads
   * waiting for a response.
   */
  protected Hashtable _threadTable;
  
  /**
   * Timeout to use while waiting for response messages, in milliseconds.
   */
  private long _timeout = 5000;
  
  
  
  /**
   * Builds a new PASTService to run on the given PastryNode.
   * @param pn PastryNode to run on
   * @param storage StorageManager used to store and retrieve files
   * @param k Replication factor to use for storing files
   *  (number of nodes to store each file on)
   */
  public PASTServiceImpl(PastryNode pn, StorageManager storage, int k) {
    super(pn);
    _pastryNode = pn;
    _storage = storage;
    _appCredentials = new PermissiveCredentials();
    _sendOptions = new SendOptions();
    _k = k;
    _threadTable = new Hashtable();
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
   * Sends a request message, blocks until a response is received,
   * and returns the response.
   * @param msg Request to send
   * @return Response to the request
   */
  protected PASTMessage _sendRequestMessage(PASTMessage msg) {
    
    /* Update the thread table so that this thread can collect its
     * response when it arrives.
     */
    ThreadTableEntry entry = new ThreadTableEntry();
    _threadTable.put(msg.getID(), entry);
    
    /* Route the request to the remote node!
     */
    sendMessage(msg);
    
    /* Wait till a response is received.
     *
     * Needs to be changed so that it works properly by 
     * subclassing Thread and giving us something that can
     * be suspended and resumed.
     */
    if(entry._msg == null) {
      try {
        synchronized (entry._waitObject) {
          entry._waitObject.wait(_timeout);
        }
      } catch (InterruptedException e) {
      }
    }
    
    /* Remove the thread from the thread table.
     */
    _threadTable.remove(msg.getID());
    
    /* Thread is here because it has been notified by a thread
     * that deposited the response message in the thread table.
     * Or because it has timed out.
     */
    PASTMessage responseMsg = entry._msg;
    
    return responseMsg;
  }
  
  /**
   * Receives a response message after a request has been sent.
   */
  protected void _handleResponseMessage(PASTMessage msg) {
    /* Response should belong to this node so deposit response message in the
     * thread table and wake up the appropriate thread.
     */
    ThreadTableEntry threadInfo = 
      (ThreadTableEntry) _threadTable.get(msg.getID());
        
    if (threadInfo == null) {
      // We don't recognize this response message, so ignore it.
      return;
    }
        
    /* This will cause the thread awaiting this message
     * to wake up and return the content to the user.
     */
    threadInfo._msg = msg;
    synchronized (threadInfo._waitObject) {
      threadInfo._waitObject.notify();
    }
  }
  
  // ---------- PASTService Methods ----------
  
  /**
   * Inserts a file into the remote PAST storage system, using the given
   * file ID.
   * @param fileId NodeId to use as a handle for the file
   * @param file File to store in PAST
   * @param authorCred Credentials of author of file
   * @return true if the file was successfully stored
   */
  public boolean insert(NodeId fileId, Persistable file, Credentials authorCred) {
    NodeId nodeId = _pastryNode.getNodeId();
    debug("Insert request for file " + fileId + " at node " + nodeId);
    MessageInsert request = new MessageInsert(nodeId, fileId, file, authorCred);
    MessageInsert response = (MessageInsert) _sendRequestMessage(request);
    if (response != null) {
      return response.getSuccess();
    }
    else {
      return false;
    }
  }
  
  /**
   * Appends an update to an existing file in the remote PAST storage system.
   * @param fileId Handle of original file
   * @param update Update to the file stored at fileId
   * @return true if the original file exists and was updated, false otherwise
   */
  public boolean append(NodeId fileId, Persistable update) {
    NodeId nodeId = _pastryNode.getNodeId();
    debug("Request to append to file " + fileId + " at node " + nodeId);
    MessageAppend request = new MessageAppend(nodeId, fileId, update);
    MessageAppend response = (MessageAppend) _sendRequestMessage(request);
    if (response != null) {
      return response.getSuccess();
    }
    else {
      return false;
    }
  }
  
  /**
   * Remotely locates and returns the file and all updates associated with fileId.
   * @param fileId Handle of original file
   * @return StorageObject with Persistable file and all updates,
   * or null if fileId not found.
   */
  public StorageObject lookup(NodeId fileId) {
    NodeId nodeId = _pastryNode.getNodeId();
    debug("Request to look up file " + fileId + " at node " + nodeId);
    MessageLookup request = new MessageLookup(nodeId, fileId);
    MessageLookup response = (MessageLookup) _sendRequestMessage(request);
    if (response != null) {
      return response.getContent();
    }
    else {
      return null;
    }

  }
  
  /**
   * Remotely reclaims the space used by the file with handle fileId,
   * effectively deleting it.
   * @param fileId Handle of original file
   * @param authorCred Credentials of user requesting the reclaim
   * @return true if the file was found and deleted, false otherwise
   */
  public boolean reclaim(NodeId fileId, Credentials authorCred) {
    NodeId nodeId = _pastryNode.getNodeId();
    System.out.println("Deleting the file with ID: " + fileId);
    MessageReclaim request = 
      new MessageReclaim(_pastryNode.getNodeId(), fileId, authorCred);
    MessageReclaim response = (MessageReclaim) _sendRequestMessage(request);
    if (response != null) {
      return response.getSuccess();
    }
    else {
      return false;
    }
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
  
  
  /**
   * Helper class used to store information on blocked threads
   * waiting for a response.
   */
  protected class ThreadTableEntry {
    /**
     * The blocked thread itself.
     */
    protected Object _waitObject;

    /**
     * The resposne message for the blocked thread
     * to pick up when its woken up.
     */
    protected PASTMessage _msg;

    /**
     * Constructor.
     */
    ThreadTableEntry() {
      this._waitObject = new Object();
      this._msg = null;
    }
  }
}