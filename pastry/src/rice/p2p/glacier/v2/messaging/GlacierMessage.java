package rice.p2p.glacier.v2.messaging;

import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.glacier.*;

public abstract class GlacierMessage implements Message {

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
}

