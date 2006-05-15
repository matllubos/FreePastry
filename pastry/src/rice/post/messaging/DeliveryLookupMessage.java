package rice.post.messaging;

import java.io.*;
import java.security.*;

import rice.post.*;

import rice.p2p.commonapi.*;
import rice.p2p.commonapi.rawserialization.*;

/**
 * This message is a request for a replica of a DRM.
 */
public class DeliveryLookupMessage extends PostMessage {
  public static final short TYPE = 2;

  private Id id;
  private NodeHandle source;

  /**
   * Constructs a DeliveryLookupMessage
   *
   * @param sender The sender of this delivery request
   * @param location The random location of this message
   */
  public DeliveryLookupMessage(PostEntityAddress sender,
                               NodeHandle source,
                               Id id) {
    super(sender);
    this.source = source;
    this.id = id;
  }

  /**
   * Gets the source of this DLM
   *
   * @return The source
   */
  public NodeHandle getSource() {
    return source;
  }

  /**
   * Gets the random locaiton of this drm
   *
   * @return The locaiton
   */
  public Id getId() {
    return id;
  }

  public DeliveryLookupMessage(InputBuffer buf, Endpoint endpoint) throws IOException {
    super(buf, endpoint);
    
    id = endpoint.readId(buf, buf.readShort());
    
    source = endpoint.readNodeHandle(buf);
  }
  
  public void serialize(OutputBuffer buf) throws IOException {
    super.serialize(buf);
    
    buf.writeShort(id.getType());
    id.serialize(buf);
    
    source.serialize(buf);
  }
  
  public short getType() {
    return TYPE;
  }
}

