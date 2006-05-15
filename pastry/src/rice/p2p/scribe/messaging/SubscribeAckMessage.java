
package rice.p2p.scribe.messaging;

import java.io.IOException;

import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.commonapi.rawserialization.*;
import rice.p2p.scribe.*;
import rice.p2p.scribe.rawserialization.ScribeContentDeserializer;

/**
 * @(#) SubscribeAckMessage.java
 *
 * The ack for a subscribe message.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */
public class SubscribeAckMessage extends AbstractSubscribeMessage {

  public static final short TYPE = 3;
  /**
   * The contained path to the root
   */
  protected Id[] pathToRoot;

  /**
   * Constructor which takes a unique integer Id
   *
   * @param id The unique id
   * @param source The source address
   * @param dest The destination address
   */
  public SubscribeAckMessage(NodeHandle source, Topic topic, Id[] pathToRoot, int id) {
    super(source, topic, id);

    this.pathToRoot = pathToRoot;
  }

  /**
   * Returns the path to the root for the node receiving
   * this message
   *
   * @return The new path to the root for the node receiving this
   * message
   */
  public Id[] getPathToRoot() {
    return pathToRoot;
  }
  
  /**
   * Returns a String representation of this ack
   *
   * @return A String
   */
  public String toString() {
    return "SubscribeAckMessage " + topic + " ID: " + id; 
  }

  /***************** Raw Serialization ***************************************/
  public short getType() {
    return TYPE; 
  }
  
  public void serialize(OutputBuffer buf) throws IOException {
    buf.writeByte((byte)0); // version
    super.serialize(buf);
    
    buf.writeInt(pathToRoot.length);
    for (int i = 0; i < pathToRoot.length; i++) {
      buf.writeShort(pathToRoot[i].getType());
      pathToRoot[i].serialize(buf);
    }    
  }
  
  public static SubscribeAckMessage build(InputBuffer buf, Endpoint endpoint) throws IOException { 
    byte version = buf.readByte();
    switch(version) {
      case 0:
        return new SubscribeAckMessage(buf, endpoint);
      default:
        throw new IOException("Unknown Version: "+version);
    }
  }
  
  /**
   * Private because it should only be called from build(), if you need to extend this,
   * make sure to build a serializeHelper() like in AnycastMessage/SubscribeMessage, and properly handle the 
   * version number.
   */
  private SubscribeAckMessage(InputBuffer buf, Endpoint endpoint) throws IOException {
    super(buf, endpoint);
    pathToRoot = new Id[buf.readInt()];
    for (int i = 0; i < pathToRoot.length; i++) {
      pathToRoot[i] = endpoint.readId(buf, buf.readShort());
    }
  }

}

