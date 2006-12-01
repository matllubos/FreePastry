
package rice.pastry.commonapi;

import java.io.IOException;

import rice.p2p.commonapi.Message;
import rice.p2p.commonapi.rawserialization.*;
import rice.p2p.util.rawserialization.JavaSerializedMessage;
import rice.pastry.NodeHandle;
import rice.pastry.messaging.*;

/**
 * This class is an internal message to the commonapi gluecode.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 * @author Peter Druschel
 */
public class PastryEndpointMessage extends PRawMessage {

  public static final short TYPE = 2;
  
  private static final long serialVersionUID = 4499456388556140871L;
  
  protected RawMessage message;
//  protected boolean isRaw = false;
  
  /**
    * Constructor.
   *
   * @param pn the pastry node that the application attaches to.
   */
  public PastryEndpointMessage(int address, Message message, NodeHandle sender) {
    this(address, message instanceof RawMessage ? (RawMessage) message : new JavaSerializedMessage(message), sender);
  }

  public PastryEndpointMessage(int address, RawMessage message, NodeHandle sender) {
    super(address);
    setSender(sender);
    this.message = message;
//    isRaw = true;
    setPriority(message.getPriority());
  }

  /**
   * Returns the internal message
   *
   * @return the credentials.
   */
  public Message getMessage() {
    if (message.getType() == 0) return ((JavaSerializedMessage)message).getMessage();
    return message;        
  }


  /**
   * Returns the internal message
  *
  * @return the credentials.
  */
 public void setMessage(Message message) {
   if (message instanceof RawMessage) {
     setMessage((RawMessage)message);
   } else {
     this.message = new JavaSerializedMessage(message);
//     isRaw = false;
   }
 }

 /**
  * Returns the internal message
   *
   * @return the credentials.
   */
  public void setMessage(RawMessage message) {
//    isRaw = true;
    this.message = message;
  }

  /**
   * Returns the String representation of this message
   *
   * @return The string
   */
  public String toString() {
    return "[PEM " + getMessage() + "]";
  }
  
  /***************** Raw Serialization ***************************************/  
  public short getType() {
    return TYPE;
  }
  
  public void serialize(OutputBuffer buf) throws IOException {
//    buf.writeBoolean(isRaw); 
    
    buf.writeByte((byte)0); // version
    // range check priority
    int priority = message.getPriority();
    if (priority > Byte.MAX_VALUE) throw new IllegalStateException("Priority must be in the range of "+Byte.MIN_VALUE+" to "+Byte.MAX_VALUE+".  Lower values are higher priority. Priority of "+message+" was "+priority+".");
    if (priority < Byte.MIN_VALUE) throw new IllegalStateException("Priority must be in the range of "+Byte.MIN_VALUE+" to "+Byte.MAX_VALUE+".  Lower values are higher priority. Priority of "+message+" was "+priority+".");
    buf.writeByte((byte)priority);

    buf.writeShort(message.getType());    
    message.serialize(buf);
//    System.out.println("PEM.serialize() message:"+message+" type:"+message.getType());
  }
  
  public PastryEndpointMessage(int address, InputBuffer buf, MessageDeserializer md, NodeHandle sender) throws IOException {
    super(address);

    byte version = buf.readByte();
    switch(version) {
      case 0:
        setSender(sender);
    //    isRaw = buf.readBoolean();
        byte priority = buf.readByte();
        short type = buf.readShort();
        if (type == 0) {
          message = new JavaSerializedMessage(md.deserialize(buf, type, priority, sender));
        } else {
          message = (RawMessage)md.deserialize(buf, type, priority, sender); 
        }
        if (getMessage() == null) throw new IOException("PEM.deserialize() message = null type:"+type+" md:"+md);
//    System.out.println("PEM.deserialize() message:"+message+" type:"+type+" md:"+md);
        break;
      default:
        throw new IOException("Unknown Version: "+version);
    }
    
  }

}




