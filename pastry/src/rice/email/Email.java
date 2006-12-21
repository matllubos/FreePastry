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
package rice.email;

import java.io.IOException;
import java.util.*;

import rice.*;
import rice.Continuation.*;
import rice.email.log.*;
import rice.email.messaging.*;
import rice.p2p.commonapi.Endpoint;
import rice.p2p.commonapi.rawserialization.*;
import rice.post.*;
import rice.post.log.*;
import rice.post.messaging.*;
import rice.post.storage.*;

/**
 * Represents a notion of a message in the POST system. This class is designed
 * to be a small representation of an Email, with pointers to all of the
 * content.
 *
 * @version $Id: pretty.settings,v 1.2 2003/07/10 03:17:16 amislove Exp $
 * @author Joe Montgomery
 * @author Derek Ruths
 */
public class Email implements java.io.Serializable {
  
  // serialveruid for backwards compatibility
  private static final long serialVersionUID = -6065011673456452566L;

  PostUserAddress sender;
  PostEntityAddress[] recipients;
  public EmailMessagePart content;
  private transient StorageService storage;

  /**
   * Constructs an Email.
   *
   * @param sender The address of the sender of the mail.
   * @param recipients The recipients for this message
   * @param content The content of this message
   */
  public Email(PostUserAddress sender,
               PostEntityAddress[] recipients,
               EmailMessagePart content) {
    this.sender = sender;
    this.recipients = recipients;
    this.content = content;
  }

  /**
   * Returns the sender of this message.
   *
   * @return The sender of this email.
   */
  public PostUserAddress getSender() {
    return this.sender;
  }

  /**
   * Returns the recipient users of this message.
   *
   * @return The recipient users of this email.
   */
  public PostEntityAddress[] getRecipients() {
    return this.recipients;
  }

  /**
   * Returns the header of this message. Should be text.
   * 
   * @return the content of the email
   */
  public EmailMessagePart getContent() {
    return content;
  }
  
  /**
   * Returns the hashcode
   *
   * @reutn the hashcode
   */
  public int hashCode() {
    int result = 928373;
    
    result ^= sender.hashCode();
    
    for (int i=0; i<recipients.length; i++)
      result ^= recipients[i].hashCode();
    
    result ^= content.hashCode();
    
    return result;
  }

  /**
   * Determines equality on this email
   *
   * @param o The object to compare to
   * @return Whether or not this email is equal
   */
  public boolean equals(Object o) {
    if (! (o instanceof Email)) {
      return false;
    }

    Email email = (Email) o;

    return (sender.equals(email.sender) &&
            Arrays.equals(recipients, email.recipients) &&
            content.equals(email.content));
  }

  /**
   * Returns a string representing this email
   *
   * @return A string representing this email
   */
  public String toString() {
    String result = "[Email from " + sender + " to ";
    for (int i = 0; i < recipients.length; i++) {
      result += recipients[i] + ", ";
    }
    result += "]";
    
    return result;
  }

  /**
   * Sets the storage service for the email. I (JM) added this method so that
   * the EmailService can set the Email's storage whenever the email is sent or
   * received, which lets the EmailClient be effectively ignorant of the storage
   * service (which is good, since this Service is part of the POST layer).
   *
   * @param s the StorageService the email is to use
   */
  protected void setStorage(StorageService s) {
    storage = s;
    content.setStorage(s);
  }

  /**
   * Stores the content of the Email into PAST and saves the references to the
   * content in the email. Should be called before the Email is sent over the
   * wire.
   *
   * @param command This command is called when the storage is done, with the
   *      Boolean value of the success of the operation, or an exception is
   *      passed to the command.
   */
  protected void storeData(final Continuation command) {
    content.storeData(command);
  }  
  
  /**
   * This method returns a list of all the handles stored in this part
   * by adding them to the specified set.
   *
   * @param set The set to add the PastContentHandles to.
   */
  public void getContentHashReferences(Set set) {
    content.getContentHashReferences(set);
  }
  
  public Email(InputBuffer buf, Endpoint endpoint) throws IOException {
    content = new EmailMessagePart(buf, endpoint);
    sender = new PostUserAddress(buf, endpoint);
    recipients = new PostEntityAddress[buf.readShort()];
    for (int i = 0; i < recipients.length; i++) {
      short type = buf.readShort();
      recipients[i] = PostEntityAddress.build(buf, endpoint, type); 
    }
  }
  
  public void serialize(OutputBuffer buf) throws IOException {
    content.serialize(buf);
    sender.serialize(buf);
    buf.writeShort((short)recipients.length);
    for(int i = 0; i < recipients.length; i++) {
      buf.writeShort(recipients[i].getType());
      recipients[i].serialize(buf); 
    }
  }
}






















