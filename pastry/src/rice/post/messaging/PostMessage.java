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

import rice.p2p.commonapi.*;
import rice.p2p.commonapi.rawserialization.*;
import rice.post.*;

/**
 * This is abstraction of all messages in the Post system.  
 */
public abstract class PostMessage implements Serializable {

  // the sender of this PostMessage
  private PostEntityAddress sender;

  /**
   * Constructs a PostMessage given the name of the
   * sender.
   *
   * @param sender The sender of this message.
   */
  public PostMessage(PostEntityAddress sender) {
    if (sender == null) {
      throw new IllegalArgumentException("Attempt to build PostMessage with null sender!");
    }
    
    this.sender = sender;
  }

  /**
   * Returns the sender of this message.
   *
   * @return The sender
   */
  public final PostEntityAddress getSender() {
    return sender;
  }

  public PostMessage(InputBuffer buf, Endpoint endpoint) throws IOException {
    sender = PostEntityAddress.build(buf, endpoint, buf.readShort()); 
  }

  public void serialize(OutputBuffer buf) throws IOException {
    buf.writeShort(sender.getType());
    sender.serialize(buf);
  }
  
  public abstract short getType();

  public static PostMessage build(InputBuffer buf, Endpoint endpoint, short type) throws IOException {
    switch(type) {
      case DeliveryLookupMessage.TYPE:
        return new DeliveryLookupMessage(buf, endpoint);
      case DeliveryLookupResponseMessage.TYPE:
        return new DeliveryLookupResponseMessage(buf, endpoint);
      case DeliveryMessage.TYPE:
        return new DeliveryMessage(buf, endpoint);
      case DeliveryRequestMessage.TYPE:
        return new DeliveryRequestMessage(buf, endpoint);
      case EncryptedNotificationMessage.TYPE:
        return new EncryptedNotificationMessage(buf, endpoint);
      case GroupNotificationMessage.TYPE:
        return new GroupNotificationMessage(buf, endpoint);
      case PresenceMessage.TYPE:
        return new PresenceMessage(buf, endpoint);        
      case ReceiptMessage.TYPE:
        return new ReceiptMessage(buf, endpoint);
    }
    throw new RuntimeException("Unknown type:"+type);
  }  
}
