package rice.ap3.messaging;

import rice.pastry.NodeId;
import rice.pastry.messaging.Message;

import java.util.Random;
import java.io.*;

/**
 * @(#) AP3Message.java
 *
 * A message in the AP3 system.
 *
 * @version $Id$
 * @author Gaurav Oberoi
 */
public class AP3Message extends Message {

  /**
   * A key for uniquely identifying the message.
   */
  protected AP3MessageID _messageID;

  /**
   * The type of the message.
   */
  protected int _messageType;

  /**
   * The content of the message as a byte array.
   */
  protected byte[] _contentBytes;

  /**
   * The fetch probability that the server fetching
   * the request should use.
   */
  protected double _fetchProbability;

  /**
   * The source of the message.
   */
  protected NodeId _source;

  /**
   * Constructor
   */
  public AP3Message(NodeId source,
		    Object content,
		    int messageType,
		    double fetchProbability) {
    super(AP3Address.instance());
    this._source = source;
    this._messageType = messageType;
    this.setContent(content);
    this._fetchProbability = fetchProbability;
    this._messageID = new AP3MessageIDImpl();
  }

  public String toString() {
    String msgType;
    if(_messageType == AP3MessageType.REQUEST) {
      msgType = "REQUEST";
    } else {
      msgType = "RESPONSE";
    }
    return "\n" + "msg.type = " + msgType + "\n" + 
      "msg.id = " + _messageID + "\n" +
      "msg.content = " + getContent() + "\n" +
      "msg.prob = " + _fetchProbability + "\n" +
      "msg.source = " + _source + "\n";
  }

  public double getFetchProbability() {
    return _fetchProbability;
  }

  public void setFetchProbability(double prob) {
    _fetchProbability = prob;
  }

  public AP3MessageID getID() { 
    return _messageID; 
  }

  public void setID(AP3MessageID messageID) {
    _messageID = messageID; 
  }

  public int getType() {
    return _messageType; 
  }

  public void setType(int messageType) { 
    _messageType = messageType; 
  }

  /**
   * Returns the content object, converted back from a byte array.
   */
  public Object getContent() {
    Object content = null;
    try {
      ByteArrayInputStream baStream = new ByteArrayInputStream(_contentBytes);
      ObjectInputStream oiStream = new ObjectInputStream(baStream);
      content = oiStream.readObject();
      oiStream.close();
      baStream.close();
    }
    catch (Exception e) {
      System.err.println("AP3 Error: Could not rebuild object from byte array: " +
        e.toString());
    }
    return content;
  }

  /**
   * Stores the given content object as a byte array.
   */
  public void setContent(Object content) {
    try {
      ByteArrayOutputStream baStream = new ByteArrayOutputStream();
      ObjectOutputStream ooStream = new ObjectOutputStream(baStream);
      ooStream.writeObject(content);
      _contentBytes = baStream.toByteArray();
      ooStream.close();
      baStream.close();
    }
    catch (Exception e) {
      System.err.println("AP3 Error: Could not write object to byte array: " +
        e.toString());
    }
  }

  public NodeId getSource() {
    return _source;
  }

  public void setSource(NodeId source) {
    _source = source;
  }
}
