package rice.past.messaging;

import rice.past.*;

import rice.storage.*;

import rice.pastry.NodeId;
import rice.pastry.messaging.Message;
import rice.pastry.security.Credentials;

import ObjectWeb.Persistence.Persistable;

import java.util.Random;
import java.io.*;

/**
 * @(#) MessageInsert.java
 *
 * PASTMessage requesting a file be inserted on the local node.
 *
 * @version $Id$
 * @author Charles Reis
 */
public class MessageInsert extends PASTMessage {
  
  /**
   * The file to insert.
   */
  protected Persistable _file;
  
  /**
   * The credentials of the author of the file.
   */
  protected Credentials _cred;
  
  /**
   * Whether the insert was successful (on a response).
   */
  protected boolean _success = false;
  
  /**
   * Builds a new request to insert a file.
   * @param nodeId Source Pastry node's ID
   * @param fileId Pastry key of file
   * @param update File to be stored
   * @param cred Credentials of the author of the file
   */
  public MessageInsert(NodeId nodeId, 
                       NodeId fileId, 
                       Persistable file, 
                       Credentials authorCred) {
    super(nodeId, fileId);
    _file = file;
    _cred = authorCred;
  }
  
  /**
   * Returns whether the insert was successful.
   */
  public boolean getSuccess() {
    return _success;
  }
  
  /**
   * Inserts this message's file into the service.
   * @param service PASTService on which to act
   */
  public void performAction(PASTServiceImpl service) {
    debug("  Inserting file " + getFileId() + " at node " +
          service.getPastryNode().getNodeId());
    StorageObject existing = service.getStorage().retrieve(getFileId());
    if (existing == null) {
      _success = service.getStorage().store(getFileId(), _file, _cred);
    }
    else {
      // Already exists, return false
      _success = false;
    }
    setType(RESPONSE);
    service.sendMessage(this);
  }
  
  /**
   * Display this message.
   */
  public String toString() {
    String val = "INSERT ";
    if (getType() == REQUEST) {
      val += "Request: ";
    }
    else {
      val += "Response: ";
    }
    return val + getFileId() + ": " + _file;
  }
}
