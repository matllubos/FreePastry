package rice.post.messaging;

import java.security.*;
import rice.post.messaging.*;
import rice.post.*;
import rice.pastry.*;
import rice.pastry.messaging.*;
import java.io.*;

/**
 * This message is a request for a replica of a DRM.
 */
public class DeliveryLookupMessage extends PostMessage {

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
}

