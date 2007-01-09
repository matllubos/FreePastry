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
package rice.post.messaging;

import java.io.*;
import java.security.*;

import rice.post.messaging.*;
import rice.post.*;

import rice.p2p.commonapi.*;
import rice.p2p.commonapi.rawserialization.*;

/**
 * This message is broadcast to the sender of a NotificationMessage in
 * order to inform the sender that the message has been received.
 */
public class DeliveryRequestMessage extends PostMessage {
  public static final short TYPE = 5;
 
  private PostUserAddress destination;
  private SignedPostMessage message;
  private Id id;

  /**
   * Constructs a DeliveryRequestMessage
   *
   * @param sender The sender of this delivery request
   * @param destination The destination address to deliver the notification to
   * @param message The message to deliver, in encrypted state
   * @param location The random location of this message
   */
  public DeliveryRequestMessage(PostEntityAddress sender, 
                                PostUserAddress destination, 
                                SignedPostMessage message,
                                Id id) {
    super(sender);
    this.destination = destination;
    this.message = message;
    this.id = id;
  }

  /**
   * Gets the destination of this notification
   *
   * @return The address of the destination of this notification
   */
  public PostUserAddress getDestination() {
    return destination;
  }
    
  /**
   * Gets the EncryptedNotificationMessage which this is a Request for. 
   * for.
   *
   * @return The internal message, in encrypted state
   */
  public SignedPostMessage getEncryptedMessage() {
    return message;
  }

  /**
   * Gets the random locaiton of this drm
   *
   * @return The locaiton
   */
  public Id getId() {
    return id;
  }
  
  public DeliveryRequestMessage(InputBuffer buf, Endpoint endpoint) throws IOException {
    super(buf, endpoint);
    
    id = endpoint.readId(buf, buf.readShort());
    
    destination = new PostUserAddress(buf, endpoint);
    message = new SignedPostMessage(buf, endpoint);
  }
  
  public void serialize(OutputBuffer buf) throws IOException {
    super.serialize(buf); 

    buf.writeShort(id.getType());
    id.serialize(buf);
    
    destination.serialize(buf);
    message.serialize(buf);    
  }
  
  public short getType() {
    return TYPE;
  }
}

