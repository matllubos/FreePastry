package rice.past.messaging;

import rice.past.*;

import rice.pastry.NodeId;
import rice.pastry.messaging.Message;

import java.util.Random;
import java.io.*;

/**
 * @(#) PASTMessage.java
 *
 * Super class for messages used in PAST.
 *
 * @version $Id$
 * @author Charles Reis
 */
public abstract class PASTMessage extends Message implements Serializable {
  
  // ----- Message Types -----
  
  public static final int REQUEST = 1;
  public static final int RESPONSE = 2;
  
  // ----- Member Fields -----
  
  /**
   * A key for uniquely identifying the message.
   */
  protected PASTMessageID _messageID;
  
  /**
   * Whether this is a request or a response.
   */
  protected int _messageType;
  
  /**
   * The fileId of the file, to be used as destination.
   */
  protected NodeId _fileId;
  
  /**
   * Constructor
   * @param source NodeId of source Pastry node
   * @param fileId NodeId of file (destination node)
   * @param messageType whether this is a request or a response
   */
  public PASTMessage(NodeId source, NodeId fileId, int messageType) {
    super(PASTAddress.instance());
    _messageID = new PASTMessageIDImpl();
    _messageType = messageType;
    _fileId = fileId;
    setSenderId(source);
  }
  
  /**
   * Constructor
   * @param source NodeId of source Pastry node
   * @param fileId NodeId of file (destination node)
   */
  public PASTMessage(NodeId source, NodeId fileId) {
    this(source, fileId, REQUEST);
  }
  
  /**
   * Gets this message's identifier.
   */
  public PASTMessageID getID() { 
    return _messageID; 
  }
  
  /**
   * Sets this message's identifier.
   * @param messageID new ID of message
   */
  public void setID(PASTMessageID messageID) {
    _messageID = messageID; 
  }
  
  /**
   * Gets this message's type.
   */
  public int getType() {
    return _messageType;
  }
  
  /**
   * Sets this message's type.
   * @param messageType REQUEST or RESPONSE
   */
  public void setType(int messageType) {
    _messageType = messageType;
  }
  
  /**
   * Gets the source NodeId for this message.
   */
  public NodeId getSource() {
    return getSenderId();
  }
  
  /**
   * Gets the fileId for this file, which is used as the destination.
   */
  public NodeId getFileId() {
    return _fileId;
  }
  
  /**
   * Performs this message's action after it is delivered.
   * @param service PASTService on which to act
   */
  public abstract void performAction(PASTServiceImpl service);
  
  /**
   * Force subclasses to implement toString.
   */
  public abstract String toString();
  
  /**
   * Print a debug message if the PASTServiceImpl.DEBUG flag is enabled.
   */
  protected void debug(String message) {
    if (PASTServiceImpl.DEBUG) {
      System.out.println("PASTMessage:  " + message);
    }
  }
}
