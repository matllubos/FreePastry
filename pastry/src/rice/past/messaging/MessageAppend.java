package rice.past.messaging;

import rice.past.*;

import rice.storage.StorageManager;

import rice.pastry.NodeId;
import rice.pastry.messaging.Message;
import rice.pastry.security.Credentials;

import ObjectWeb.Persistence.Persistable;

import java.util.Random;
import java.io.*;

/**
 * @(#) MessageAppend.java
 *
 * PASTMessage requesting an update to a file be stored on the local node.
 *
 * @version $Id$
 * @author Charles Reis
 */
public class MessageAppend extends PASTMessage {
  
  /**
   * The update to insert.
   */
  protected Persistable _update;
  
  /**
   * The credentials of the author of the update.
   */
  protected Credentials _cred;
  
  /**
   * Whether the insert was successful (on a response).
   */
  protected boolean _success = false;
  
  /**
   * Builds a new request to append an update to a file.
   * @param nodeId Source Pastry node's ID
   * @param fileId Pastry key of original file
   * @param update Update to be appended to file
   * @param authorCred Credentials of the author of the update
   */
  public MessageAppend(NodeId nodeId, 
                       NodeId fileId, 
                       Persistable update,
                       Credentials authorCred) {
    super(nodeId, fileId);
    _update = update;
    _cred = authorCred;
  }
  
  /**
   * Returns whether the append was successful.
   */
  public boolean getSuccess() {
    return _success;
  }
  
  /**
   * Appends this message's update to the file in the service.
   * @param service PASTService on which to act
   */
  public void performAction(PASTServiceImpl service) {
    debug("  Appending to file " + getFileId() + " at node " +
          service.getPastryNode().getNodeId());
    _success = service.getStorage().update(getFileId(), _update, _cred);
    setType(RESPONSE);
    service.sendMessage(this);
  }
  
  /**
   * Display this message.
   */
  public String toString() {
    String val = "APPEND ";
    if (getType() == REQUEST) {
      val += "Request: ";
    }
    else {
      val += "Response: ";
    }
    return val + getFileId() + ": " + _update;
  }
}
