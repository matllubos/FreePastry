package rice.past.messaging;

import rice.*;

import rice.past.*;

import rice.pastry.NodeId;
import rice.pastry.messaging.Message;
import rice.pastry.security.Credentials;

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
  protected Serializable _content = null;
  
  /**
   * Builds a new request to lookup an existing file.
   * @param nodeId Source Pastry node's ID
   * @param fileId Pastry key of desired file
   */
  public MessageLookup(NodeId nodeId, NodeId fileId) {
    super(nodeId, fileId);
  }
  
  /**
   * Returns the located storage object.
   */
  public Serializable getContent() {
    return _content;
  }
  
  /**
   * Looks up the file in the given service's storage.
   * @param service PASTService on which to act
   */
  public void performAction(final PASTServiceImpl service) {
    debug("  Looking up file " + getFileId() + " at node " +
          service.getPastryNode().getNodeId());

    Continuation lookup = new Continuation() {
      public void receiveResult(Object o) {
        _content = (Serializable) o;
        setType(RESPONSE);
        service.sendMessage(MessageLookup.this);
      }

      public void receiveException(Exception e) {
        System.out.println("Exception " + e + " occurred during an insert!");
      }
    };
    
    service.getStorage().getObject(getFileId(), lookup);
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
