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
package rice.email.messaging;

import rice.email.*;
import rice.p2p.commonapi.Endpoint;
import rice.p2p.commonapi.rawserialization.*;
import rice.post.*;
import rice.post.messaging.*;
import rice.post.rawserialization.*;

import java.io.*;

/**
 * This class represents an notification in the email service that
 * a new email is available for the recipient of this email.
 */
public class EmailNotificationMessage extends NotificationMessage implements Raw, Serializable {
  public static final short TYPE = 15;
  private Email _email;
    
  /**
   * Constructs a EmailNotificationMessage for the given Email.
   *
   * @param email The email that is available
   * @param recipient the PostUserAddress to recieve the Email
   * @param service the EmailService to use to send the message
   */
  public EmailNotificationMessage(Email email, PostEntityAddress recipient, EmailService service) {
    super(service.getAddress(), email.getSender(), recipient);
    _email = email;
  }
  
  /**
   * Returns the email which this notification is for.
   *
   * @return The Email contained in this notification
   */
  public Email getEmail() {
    return _email;
  }
  
  public EmailNotificationMessage(InputBuffer buf, Endpoint endpoint) throws IOException {
    super(buf, endpoint);
    _email = new Email(buf, endpoint);
  }
  
  public void serialize(OutputBuffer buf) throws IOException {
    super.serialize(buf);
    
    _email.serialize(buf);
  }
  
  public short getType() {
    return TYPE; 
  }
}
