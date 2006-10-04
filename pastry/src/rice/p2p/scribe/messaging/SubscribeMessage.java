
package rice.p2p.scribe.messaging;

import java.io.IOException;

import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.commonapi.rawserialization.*;
import rice.p2p.scribe.*;
import rice.p2p.scribe.rawserialization.*;

/**
 * @(#) SubscribeMessage.java The subscribe message.
 *
 * @version $Id$
 * @author Alan Mislove
 */
public class SubscribeMessage extends AnycastMessage {
  public static final short TYPE = 2;

  /**
   * The original subscriber
   */
  protected NodeHandle subscriber;

  /**
   * The previous parent
   */
  protected Id previousParent;

  /**
   * The id of this message
   */
  protected int id;

  /**
   * Constructor which takes a unique integer Id
   *
   * @param source The source address
   * @param topic The topic
   * @param id The UID for this message
   * @param content The content
   */
  public SubscribeMessage(NodeHandle source, Topic topic, int id, RawScribeContent content) {
    this(source, topic, null, id, content);
  }

  /**
   * Constructor which takes a unique integer Id
   *
   * @param source The source address
   * @param topic The topic
   * @param id The UID for this message
   * @param content The content
   * @param previousParent The parent on this topic who died
   */
  public SubscribeMessage(NodeHandle source, Topic topic, Id previousParent, int id, RawScribeContent content) {
    super(source, topic, content);

    this.id = id;
    this.subscriber = source;
    this.previousParent = previousParent;
  }

  /**
   * Returns the node who is trying to subscribe
   *
   * @return The node who is attempting to subscribe
   */
  public NodeHandle getSubscriber() {
    return subscriber;
  }

  /**
   * Returns the node who is trying to subscribe
   *
   * @return The node who is attempting to subscribe
   */
  public Id getPreviousParent() {
    return previousParent;
  }

  /**
   * Returns this subscribe lost message's id
   *
   * @return The id of this subscribe message
   */
  public int getId() {
    return id;
  }

  /**
   * Returns a String represneting this message
   *
   * @return A String of this message
   */
  public String toString() {
    return "[SubscribeMessage " + topic + " subscriber " + subscriber + " ID " + id + "]";
  }

  /***************** Raw Serialization ***************************************/
  public short getType() {
    return TYPE; 
  }
  
  public void serialize(OutputBuffer buf) throws IOException {
    buf.writeByte((byte)0); // version
    super.serializeHelper(buf);
    buf.writeInt(id);
    if (previousParent == null) {
      buf.writeBoolean(false);      
    } else {
      buf.writeBoolean(true);
      buf.writeShort(previousParent.getType());
      previousParent.serialize(buf);      
    }
    subscriber.serialize(buf);
  }
  
  public static SubscribeMessage buildSM(InputBuffer buf, Endpoint endpoint, ScribeContentDeserializer scd) throws IOException {
    byte version = buf.readByte();
    switch(version) {
      case 0:
        return new SubscribeMessage(buf, endpoint, scd);
      default:
        throw new IOException("Unknown Version: "+version);
    }
  }
  
  /**
   * Private because it should only be called from build(), if you need to extend this,
   * make sure to build a serializeHelper() like in AnycastMessage/SubscribeMessage, and properly handle the 
   * version number.
   */
  private SubscribeMessage(InputBuffer buf, Endpoint endpoint, ScribeContentDeserializer contentDeserializer) throws IOException {
    super(buf, endpoint, contentDeserializer);
    id = buf.readInt();
    if (buf.readBoolean())
      previousParent = endpoint.readId(buf, buf.readShort());
    subscriber = endpoint.readNodeHandle(buf);
  }
  
  
}

