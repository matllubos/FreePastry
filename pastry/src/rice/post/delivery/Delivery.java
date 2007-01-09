/*******************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate

Copyright 2002-2007, Rice University. Copyright 2006-2007, Max Planck Institute 
for Software Systems.  All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither the name of Rice  University (RICE), Max Planck Institute for Software 
Systems (MPI-SWS) nor the names of its contributors may be used to endorse or 
promote products derived from this software without specific prior written 
permission.

This software is provided by RICE, MPI-SWS and the contributors on an "as is" 
basis, without any representations or warranties of any kind, express or implied 
including, but not limited to, representations or warranties of 
non-infringement, merchantability or fitness for a particular purpose. In no 
event shall RICE, MPI-SWS or contributors be liable for any direct, indirect, 
incidental, special, exemplary, or consequential damages (including, but not 
limited to, procurement of substitute goods or services; loss of use, data, or 
profits; or business interruption) however caused and on any theory of 
liability, whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even if 
advised of the possibility of such damage.

*******************************************************************************/ 

package rice.post.delivery;

import java.io.*;

import rice.*;
import rice.post.*;
import rice.post.messaging.*;
import rice.post.security.*;
import rice.p2p.past.*;
import rice.p2p.past.gc.*;
import rice.p2p.past.gc.rawserialization.*;
import rice.p2p.commonapi.*;
import rice.p2p.commonapi.rawserialization.*;
import rice.p2p.multiring.*;
import rice.p2p.util.*;

/**
 * The delivery stored in Past
 *
 * @version $Id$
 * @author Alan Mislove
 * @author Ansley Post
 * @author Peter Druschel
 */
public class Delivery extends ContentHashPastContent implements RawGCPastContent {
  
  public static final short TYPE = 1;
  
  // serialver for backward compatibility
  private static final long serialVersionUID = 5154309973324809945L;
  
  /**
   * The internal encrypted message
   */
  protected SignedPostMessage message;
  
  /**
   * Constructor which takes the wrapped message
   *
   * @param message The message to deliver
   */
  protected Delivery(SignedPostMessage message, IdFactory factory) {
    super(null);
    this.message = message;
    
    try {
      if (factory instanceof MultiringIdFactory) {
        MultiringIdFactory mFactory = (MultiringIdFactory) factory;
        EncryptedNotificationMessage enm = (EncryptedNotificationMessage) message.getMessage();
        this.myId = mFactory.buildRingId(((RingId) enm.getDestination().getAddress()).getRingId(), 
                                         SecurityUtils.hash(SecurityUtils.serialize(message)));
      } else {
        this.myId = factory.buildId(SecurityUtils.hash(SecurityUtils.serialize(message)));
      }
    } catch (IOException e) {
      throw new IllegalArgumentException("Setting myId caused: " + e);
    }
  }
  
  /**
   * Constructor which takes the wrapped message
   *
   * @param message The message to deliver
   */
  protected Delivery(SignedPostMessage message, Id id) {
    super(id);
    this.message = message;
  }

  /**
   * Returns the internal signed message
   *
   * @return The wrapped message
   */
  public SignedPostMessage getSignedMessage() {
    return message;
  }
  
  /**
   * Returns the internal message
   *
   * @return The wrapped message
   */
  public EncryptedNotificationMessage getMessage() {
    return (EncryptedNotificationMessage) message.getMessage();
  }
  
  /**
   * Returns the version number associated with this PastContent object - 
   * version numbers are designed to be monotonically increasing numbers which
   * signify different versions of the same object.
   *
   * @return The version number of this object
   */
  public long getVersion() {
    return 0L;
  }
  
  /**
    * Produces a handle for this content object. The handle is retrieved and returned to the
   * client as a result of the Past.lookupHandles() method.
   *
   * @param The local GCPast service which the content is on.
   * @return the handle
   */
  public GCPastContentHandle getHandle(GCPast local, long expiration) {
    return new DeliveryHandle(getId(), local.getLocalNodeHandle(), expiration);
  }
  
  /**
   * Returns the metadata which should be stored with this object.  Allows applications
   * to add arbitrary items into the object's metadata.
   *
   * @param The local GCPast service which the content is on.
   * @return the handle
   */
  public GCPastMetadata getMetadata(long expiration) {
    return new DeliveryMetadata(expiration, getMessage().getDestination());
  }
  
  public Delivery(InputBuffer buf, Endpoint endpoint) throws IOException {
    super(endpoint.readId(buf, buf.readShort()));
    message = new SignedPostMessage(buf, endpoint);
  }
  
  public void serialize(OutputBuffer buf) throws IOException {
    buf.writeShort(this.myId.getType());
    this.myId.serialize(buf);
    message.serialize(buf); 
  }
  
  public short getType() {
    return TYPE; 
  }
  
  public String toString() {
    return "Delivery["+message+"]"; 
  }
  
  protected static class DeliveryHandle implements GCPastContentHandle {
    
    // config variables
    protected Id id;
    protected NodeHandle handle;
    protected long expiration;
    
    /**
     * Constructor
     *
     */
    public DeliveryHandle(Id id, NodeHandle handle, long expiration) {
      this.id = id;
      this.handle = handle;
      this.expiration = expiration;
    }
    
    /**
     * get the id of the PastContent object associated with this handle
     * @return the id
     */
    public Id getId() { 
      return id; 
    }
    
    /**
     * get the NodeHandle of the Past node on which the object associated with this handle is stored
     * @return the id
     */
    public NodeHandle getNodeHandle() {
      return handle;
    }
    
    /**
     * Returns the version number associated with this PastContentHandle - 
     * version numbers are designed to be monotonically increasing numbers which
     * signify different versions of the same object.
     *
     * @return The version number of this object
     */
    public long getVersion() {
      return 0L;
    }
    
    /**
     * Returns the current expiration time of this object.
     *
     * @return The current expiration time of this object
     */
    public long getExpiration() {
      return expiration;
    }
  }
}





