package rice.past.messaging;

import rice.*;

import rice.past.*;

import rice.pastry.NodeId;
import rice.pastry.messaging.Message;
import rice.pastry.security.Credentials;

import java.util.Random;
import java.io.*;

/**
 * @(#) MessageReclaim.java
 *
 * PASTMessage requesting that space used for a given file on
 * the local node be reclaimed, effectively deleting the file.
 *
 * @version $Id$
 * @author Charles Reis
 */
public class MessageReclaim extends PASTMessage {
  
  /**
   * Whether the reclaim request was successful
   */
  protected boolean _success = false;
  
  /**
   * The credentials of the user trying to delete
   */
  protected Credentials _cred;
  
  /**
   * Builds a new request to reclaim space used by an existing file.
   * @param nodeId Source Pastry node's ID
   * @param fileId Pastry key of desired file
   * @param cred Credentials of user requesting the reclaim
   */
  public MessageReclaim(NodeId nodeId, NodeId fileId, Credentials cred) {
    super(nodeId, fileId);
    _cred = cred;
  }
  
  /**
   * Returns whether the reclaim command was successful.
   */
  public boolean getSuccess() {
    return _success;
  }
  
  /**
   * Reclaims the space in the service's storage.
   * @param service PASTService on which to act
   */
  public void performAction(final PASTServiceImpl service) {
    debug("  Reclaiming file " + getFileId() + " at node " +
          service.getPastryNode().getNodeId());

    Continuation reclaim = new Continuation() {
      public void receiveResult(Object o) {
        _success = ((Boolean) o).booleanValue();
        setType(RESPONSE);
        service.sendMessage(MessageReclaim.this);
      }

      public void receiveException(Exception e) {
        System.out.println("Exception " + e + " occurred during an insert!");
      }
    };

    service.getStorage().unstore(getFileId(), reclaim);
  }
  
  /**
   * Display this message.
   */
  public String toString() {
    String val = "RECLAIM ";
    if (getType() == REQUEST) {
      val += "Request: ";
    }
    else {
      val += "Response: ";
    }
    return val + getFileId();
  }
}
