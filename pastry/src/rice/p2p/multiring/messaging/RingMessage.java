
package rice.p2p.multiring.messaging;

import java.io.IOException;
import java.util.Hashtable;

import rice.p2p.commonapi.*;
import rice.p2p.commonapi.rawserialization.*;
import rice.p2p.multiring.*;
import rice.p2p.scribe.*;
import rice.p2p.scribe.rawserialization.RawScribeContent;
import rice.p2p.util.JavaSerializedMessage;

/**
 * @(#) RingMessage.java
 *
 * This class the abstraction of a message used internally by the multiring hierarchy.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */
public class RingMessage implements RawScribeContent {
  
  // serialver for backward compatibility
  private static final long serialVersionUID = -7097995807488121199L;

  public static final short TYPE = 1;

  /** 
   * The target of this ring message
   */
  protected RingId id;
  
  /**
   * The internal message to be sent
   */
  protected RawMessage message;
  
  /**
   * The name of the application which sent this message
   */
  protected String application;

  /**
   * Constructor which takes a unique integer Id
   *
   * @param id The unique id
   * @param source The source address
   * @param dest The destination address
   */
//  public RingMessage(RingId id, Message message, String application) {
//    this(id, message instanceof RawMessage ? (RawMessage) message : new JavaSerializedMessage(message), application);
//  }
//  
  public RingMessage(RingId id, RawMessage message, String application) {
    this.id = id;
    this.message = message;
    this.application = application;
  }
  
  /**
   * Method which should return the priority level of this message.  The messages
   * can range in priority from 0 (highest priority) to Integer.MAX_VALUE (lowest) -
   * when sending messages across the wire, the queue is sorted by message priority.
   * If the queue reaches its limit, the lowest priority messages are discarded.  Thus,
   * applications which are very verbose should have LOW_PRIORITY or lower, and
   * applications which are somewhat quiet are allowed to have MEDIUM_PRIORITY or
   * possibly even HIGH_PRIORITY.
   *
   * @return This message's priority
   */
  public byte getPriority() {
    return message.getPriority();
  }

  /**
   * Method which returns this messages'  id
   *
   * @return The id of this message
   */
  public RingId getId() {
    return id;
  }

  /**
   * Method which returns this messages' internal message
   *
   * @return The internal message of this message
   */
  public RawMessage getMessage() {
    return message;
  }

  /**
   * Method which returns this messages' applicaiton name
   *
   * @return The application name of this message
   */
  public String getApplication() {
    return application;
  }

  public short getType() {
    return TYPE;
  }

  public void serialize(OutputBuffer buf) throws IOException {
    id.serialize(buf);
    buf.writeUTF(application);
    buf.writeShort(message.getType());
    buf.writeByte(message.getPriority());
//    message.getSender().seria
    message.serialize(buf);
  }
  
  /**
   * TODO: This can probably be done more efficiently, IE, deserialize the message on getMessage().  Can do that later.
   * @param buf
   * @param endpoint
   * @param md
   * @param sender
   * @param priority
   * @throws IOException
   */
  public RingMessage(InputBuffer buf, Endpoint ringEndpoint, Hashtable endpoints) throws IOException {
    id = new RingId(buf, ringEndpoint);
    application = buf.readUTF();

    // this code finds the proper deserializer
    Endpoint endpoint = (Endpoint)endpoints.get(application);
    if (endpoint == null) {
      throw new IOException("Couldn't find application:"+application); 
    }
    MessageDeserializer md = endpoint.getDeserializer();
    
    short type = buf.readShort();
    byte priority = buf.readByte();
    // Jeff - 3/31/06 not sure if this is the correct decision, but I don't know how to get the sender
//    NodeHandle sender = endpoint.readNodeHandle(buf);
    Message m = md.deserialize(buf, type, priority, null);
    if (type == 0) {
      message = new JavaSerializedMessage(m);
    } else {
      message = (RawMessage)m; 
    }
  }
  
}

