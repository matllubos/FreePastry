package rice.post.delivery;

import java.io.*;

import rice.*;
import rice.post.*;
import rice.post.messaging.*;
import rice.post.security.*;
import rice.p2p.past.*;
import rice.p2p.past.gc.*;
import rice.p2p.commonapi.*;
import rice.p2p.multiring.*;

/**
 * The delivery metadata stored in Past
 *
 * @version $Id$
 * @author Alan Mislove
 * @author Ansley Post
 * @author Peter Druschel
 */
public class DeliveryMetadata extends GCPastMetadata {
  
  private static final long serialVersionUID = -8357987542721320878L;
  
  protected PostEntityAddress destination;
  
  public DeliveryMetadata(long expiration, PostEntityAddress address) {
    super(expiration);
    this.destination = address;
  }
  
  public PostEntityAddress getDestination() {
    return destination;
  }
  
  public GCPastMetadata setExpiration(long expiration) {
    return new DeliveryMetadata(expiration, destination);
  }
  
  public boolean equals(Object o) {
    return super.equals(o) && ((DeliveryMetadata) o).destination.equals(destination);
  }
  
  public int hashCode() {
    return super.hashCode() ^ destination.hashCode();
  }
}
