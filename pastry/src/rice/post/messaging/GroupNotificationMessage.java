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

import java.io.IOException;
import java.util.*;

import rice.p2p.commonapi.Endpoint;
import rice.p2p.commonapi.rawserialization.*;
import rice.post.*;

/**
 * This class represents a notification message which is sent to a group.
 */
public class GroupNotificationMessage extends PostMessage {
  public static final short TYPE = 7;

  private PostGroupAddress group;

  private byte[] data;

  /**
   * Constructs a GroupNotificationMessage
   *
   * @param key The encrypted key
   * @param data The encrypted NotificationMessage
   */
  public GroupNotificationMessage(PostEntityAddress sender, PostGroupAddress group, byte[] data) {
    super(sender);
    this.group = group;
    this.data = data;
  }

  /**
   * Returns the group of the NotificationMessage
   *
   * @return The group
   */
  public PostGroupAddress getGroup() {
    return group;
  }

  /**
   * Returns the ciphertext of the NotificationMessage
   *
   * @return The ciphertext.
   */
  public byte[] getData() {
    return data;
  }

  public boolean equals(Object o) {
    if (! (o instanceof GroupNotificationMessage))
      return false;

    return Arrays.equals(data, ((GroupNotificationMessage) o).getData());
  }


  public GroupNotificationMessage(InputBuffer buf, Endpoint endpoint) throws IOException {
    super(buf, endpoint);
    
    group = new PostGroupAddress(buf, endpoint);
    
    data = new byte[buf.readInt()];
    buf.read(data);
  }
  
  public void serialize(OutputBuffer buf) throws IOException {
    super.serialize(buf); 
    
    group.serialize(buf);
    
    buf.writeInt(data.length);
    buf.write(data, 0, data.length);
  }
  
  public short getType() {
    return TYPE;
  }
}
