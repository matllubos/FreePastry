/*************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate

Copyright 2002, Rice University. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither  the name  of Rice  University (RICE) nor  the names  of its
contributors may be  used to endorse or promote  products derived from
this software without specific prior written permission.

This software is provided by RICE and the contributors on an "as is"
basis, without any representations or warranties of any kind, express
or implied including, but not limited to, representations or
warranties of non-infringement, merchantability or fitness for a
particular purpose. In no event shall RICE or contributors be liable
for any direct, indirect, incidental, special, exemplary, or
consequential damages (including, but not limited to, procurement of
substitute goods or services; loss of use, data, or profits; or
business interruption) however caused and on any theory of liability,
whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even
if advised of the possibility of such damage.

********************************************************************************/

package rice.post.delivery;

import java.io.*;

import rice.*;
import rice.post.*;
import rice.post.messaging.*;
import rice.post.security.*;
import rice.p2p.past.*;
import rice.p2p.commonapi.*;

/**
 * The delivery stored in Past
 *
 * @version $Id$
 * @author Alan Mislove
 * @author Ansley Post
 * @author Peter Druschel
 */
public class Delivery extends ContentHashPastContent {
  
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
    this.message = message;
    
    try {
      this.myId = factory.buildId(SecurityUtils.hash(SecurityUtils.serialize(message)));
    } catch (IOException e) {
      throw new IllegalArgumentException("Setting myId caused: " + e);
    }
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
  
}





