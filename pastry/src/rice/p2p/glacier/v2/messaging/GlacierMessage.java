package rice.p2p.glacier.v2.messaging;

import java.io.IOException;

import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.commonapi.rawserialization.*;
import rice.p2p.glacier.*;

public abstract class GlacierMessage implements RawMessage {
  
  // serialver for backward compatibility
  private static final long serialVersionUID = -5849182107707420256L;

  // the unique id for this message
  protected int id;

  // the tag of this message
  protected char tag;

  // the source Id of this message
  protected NodeHandle source;

  // the destination of this message
  protected Id dest;
  
  // true if this is a response to a previous message
  protected boolean isResponse;
  
  /**
   * Constructor which takes a unique integer Id
   *
   * @param id The unique id
   * @param source The source address
   * @param dest The destination address
   */
  protected GlacierMessage(int id, NodeHandle source, Id dest, boolean isResponse, char tag) {
    this.id = id;
    this.source = source;
    this.dest = dest;
    this.isResponse = isResponse;
    this.tag = tag;
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
    return LOW_PRIORITY;
  }

  /**
   * Method which returns this messages' unique id
   *
   * @return The id of this message
   */
  public int getUID() {
    return id;
  }

  /**
   * Method which returns this messages' source address
   *
   * @return The source of this message
   */
  public NodeHandle getSource() {
    return source;
  }

  /**
   * Method which returns this messages' destination address
   *
   * @return The dest of this message
   */
  public Id getDestination() {
    return dest;
  }
  
  public boolean isResponse() {
    return isResponse;
  }
  
  public char getTag() {
    return tag;
  }
  
  public void serialize(OutputBuffer buf) throws IOException {
    buf.writeInt(id); 
    buf.writeChar(tag);
    buf.writeBoolean(isResponse);
    buf.writeShort(dest.getType());
    dest.serialize(buf);
    source.serialize(buf);
  }
  
  public GlacierMessage(InputBuffer buf, Endpoint endpoint) throws IOException {    
    id = buf.readInt();
    tag = buf.readChar();
    isResponse = buf.readBoolean();
    dest = endpoint.readId(buf, buf.readShort()); 
    source = endpoint.readNodeHandle(buf);
  }  
}

