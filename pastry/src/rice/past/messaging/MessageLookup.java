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
 * @(#) MessageLookup.java
 *
 * PASTMessage requesting a file be retrieved from the local node.
 *
 * @version $Id$
 * @author Charles Reis
 */
public class MessageLookup extends PASTMessage {
  
  /**
   * Content to be returned
   */
  protected StorageObject _content = null;
  
  /**
   * Builds a new request to lookup an existing file.
   * @param nodeId Source Pastry node's ID
   * @param fileId Pastry key of desired file
   */
  public MessageLookup(NodeId nodeId, NodeId fileId) {
    super(nodeId, fileId);
  }
  
  /**
   * Returns whether the insert was successful.
   */
  public StorageObject getContent() {
    return _content;
  }
  
  /**
   * Looks up the file in the given service's storage.
   * @param service PASTService on which to act
   */
  public void performAction(PASTServiceImpl service) {
    debug("  Looking up file " + getFileId() + " at node " +
          service.getPastryNode().getNodeId());
    _content = service.getStorage().lookup(getFileId());
    setType(RESPONSE);
    service.sendMessage(this);
  }
  
  /**
   * Display this message.
   */
  public String toString() {
    String val = "LOOKUP ";
    if (getType() == REQUEST) {
      val += "Request: ";
    }
    else {
      val += "Response: ";
    }
    return val + getFileId();
  }
}
