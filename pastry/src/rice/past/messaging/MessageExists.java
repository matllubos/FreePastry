package rice.past.messaging;

import rice.*;

import rice.past.*;

import rice.pastry.NodeId;
import rice.pastry.messaging.Message;
import rice.pastry.security.Credentials;

import java.util.Random;
import java.io.*;

/**
 * @(#) MessageExists.java
 *
 * PASTMessage detemining if a file exists at a given id.
 *
 * @version $Id$
 * @author Charles Reis
 */
public class MessageExists extends PASTMessage {
  
  /**
   * Content to be returned
   */
  protected boolean _exists = false;
  
  /**
   * Builds a new request to see if a file exists.
   * @param nodeId Source Pastry node's ID
   * @param fileId Pastry key of desired file
   */
  public MessageExists(NodeId nodeId, NodeId fileId) {
    super(nodeId, fileId);
  }
  
  /**
   * Returns whether the file exists.
   */
  public boolean exists() {
    return _exists;
  }
  
  /**
   * Looks up the file in the given service's storage.
   * @param service PASTService on which to act
   */
  public void performAction(final PASTServiceImpl service) {
    debug("  Seeing if file " + getFileId() + " exists, at node " +
          service.getPastryNode().getNodeId());

    Continuation c = new Continuation() {
      public void receiveResult(Object o) {
        _exists = ((Boolean) o).booleanValue();

        debug("File was found (" + _exists + ") at " + service.getPastryNode().getNodeId());
        
        setType(RESPONSE);
        service.sendMessage(MessageExists.this);
      }

      public void receiveException(Exception e) {
        System.out.println("Exception " + e + " occured during an exists call.");
      }
    };

    service.getStorage().exists(getFileId(), c);
  }
  
  /**
   * Display this message.
   */
  public String toString() {
    String val = "EXISTS ";
    if (getType() == REQUEST) {
      val += "Request: ";
    }
    else {
      val += "Response: ";
    }
    return val + getFileId();
  }
}
