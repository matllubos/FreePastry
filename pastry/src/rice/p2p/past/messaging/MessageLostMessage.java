
package rice.p2p.past.messaging;

import java.io.IOException;

import rice.*;
import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.p2p.commonapi.*;
import rice.p2p.commonapi.rawserialization.*;
import rice.p2p.past.*;
import rice.p2p.util.rawserialization.JavaSerializedMessage;

/**
 * @(#) MessageLostMessage.java
 *
 * This class represents a reminder to Past that an outstanding message exists,
 * and that the waiting continuation should be informed if the message is lost.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 * @author Ansley Post
 * @author Peter Druschel
 */
public class MessageLostMessage extends PastMessage {
  public static final short TYPE = 7;

  private static final long serialVersionUID = -8664827144233122095L;

  // the id the message was sent to
  protected Id id;
  
  // the hint the message was sent to
  protected NodeHandle hint;
  
  // the message
  protected String messageString;

  /**
   * Constructor which takes a unique integer Id and the local id
   *
   * @param uid The unique id
   * @param local The local nodehandle
   */
  public MessageLostMessage(int uid, NodeHandle local, Id id, Message message, NodeHandle hint) {
    super(uid, local, local.getId());

    setResponse();
    this.hint = hint;
    if (message != null) {
      this.messageString = message.toString();
    } else {
      this.messageString = "";
    }
    this.id = id;
  }

  /**
   * Method by which this message is supposed to return it's response -
   * in this case, it lets the continuation know that a the message was
   * lost via the receiveException method.
   *
   * @param c The continuation to return the reponse to.
   */
  public void returnResponse(Continuation c, Environment env, String instance) {
    Logger logger = env.getLogManager().getLogger(getClass(), instance);
    Exception e = new PastException("Outgoing message '" + messageString + "' to " + id + "/" + hint + " was lost - please try again.");
    if (logger.level <= Logger.WARNING) logger.logException("ERROR: Outgoing PAST message " + messageString + " with UID " + getUID() + " was lost", e);
    c.receiveException(e);
  }

  /**
  * Returns a string representation of this message
   *
   * @return A string representing this message
   */
  public String toString() {
    return "[MessageLostMessage]";
  }

  public short getType() {
    return TYPE; 
  }
  
  public void serialize(OutputBuffer buf) throws IOException {
    throw new RuntimeException("serialize() not supported in MessageLostMessage"); 
    
//    super.serialize(buf); 
//    
//    if (hint == null) {
//      buf.writeBoolean(false); 
//    } else {
//      buf.writeBoolean(true); 
//      hint.serialize(buf);
//    }
//    
//    if (id == null) {
//      buf.writeBoolean(false); 
//    } else {
//      buf.writeBoolean(true); 
//      buf.writeShort(id.getType());
//      id.serialize(buf);
//    }
//    
//    buf.writeUTF(messageString);
  }

//  public MessageLostMessage(InputBuffer buf, Endpoint endpoint) throws IOException {
//    super(buf, endpoint);
//
//    if (buf.readBoolean()) {
//      hint = endpoint.readNodeHandle(buf);
//    }
//    if (buf.readBoolean()) {
//      id = endpoint.readId(buf, buf.readShort());
//    }
//    
//    messageString = buf.readUTF();    
//  }  
}

