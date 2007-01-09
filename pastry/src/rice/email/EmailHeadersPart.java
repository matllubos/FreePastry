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
package rice.email;

import java.io.*;
import java.lang.ref.*;
import java.security.*;
import java.util.*;

import rice.*;
import rice.Continuation.*;
import rice.p2p.commonapi.Endpoint;
import rice.p2p.commonapi.rawserialization.*;
import rice.pastry.*;
import rice.post.storage.*;

/**
 * Abstract class which represents a part of an email with headers
 *
 * @author Alan Mislove
 */
public class EmailHeadersPart extends EmailContentPart {
  public static final short TYPE = 1;
  
  // serialver uid
  private static final long serialVersionUID = 1186745194337869017L;

  /**
   * The data representing the haeders (transient as it is stored).
   */
  protected transient SoftReference headers;

  /**
   * A reference to the headers of this email part
   */
  public EmailDataReference headersReference;

  /**
   * The content of this part
   */
  public EmailContentPart content;
  
  /**
   * A reference to the headers which is non-soft
   */
  protected transient EmailData unstoredHeaders;

  /**
    * Constructor. Takes in a emailData representing the headers and
   * a EmailContentPart representing the content
   *
   * @param headers The headers of this part
   * @param content The content of this part
   */
  public EmailHeadersPart(EmailData headers, EmailContentPart content) {
    super(headers.getData().length + content.getSize());
    this.unstoredHeaders = headers;
    this.headers = new SoftReference(headers);
    this.content = content;
  }

  /**
   * Method which sets this part's storage service
   *
   * @param storage The local storage service
   */
  public void setStorage(StorageService storage) {
    super.setStorage(storage);
    content.setStorage(storage);
  }
  
  /**
   * This method returns a list of all the handles stored in this part
   * by adding them to the specified set.
   *
   * @param set The set to add the PastContentHandles to.
   */
  public void getContentHashReferences(Set set) {
    set.add(headersReference);
    content.getContentHashReferences(set);
  }

  /**
   * Returns the headers of this EmailPart to the continuation
   *
   * @param commmand The command to run once the result has been
   *   obtained
   */
  public void getHeaders(Continuation command) {
    EmailData data = null;

    if (((data = unstoredHeaders) != null) ||
        ((headers != null) && ((data = (EmailData) headers.get()) != null))) {
      command.receiveResult(data);
      return;
    }
    
    storage.retrieveContentHash(headersReference, new StandardContinuation(command) {
      public void receiveResult(Object o) {
        headers = new SoftReference((EmailData) o);

        parent.receiveResult(o);
      }
    });
  }

  /**
   * Returns the content of this part
   *
   * @return The content of this part
   */
  public void getContent(Continuation command) {
    command.receiveResult(content);
  }

  /**
   * Method which writes this part's headers out to disk
   * and retrieves a reference to it.
   *
   * @param command The command to run once the data has been stored, and
   *   is returned the success or failure of this command
   */
  public void storeData(Continuation command) {
    if ((headersReference != null) || (unstoredHeaders == null)) {
      command.receiveResult(Boolean.TRUE);
      return;
    }

    storage.storeContentHash(unstoredHeaders, new StandardContinuation(command) {
      public void receiveResult(Object o) {
        headersReference = (EmailDataReference) o;
        unstoredHeaders = null;
        
        content.storeData(parent);
      }
    });
  }
  
  /**
   * Returns the hashCode
   *
   */
  public int hashCode() {
    return headersReference.hashCode() ^ content.hashCode();
  }

  /**
   * Returns whether or not this emailHeadersPart is equal to the
   * given object
   *
   * @param o The object to compare to
   * @return Whether or not this is equal to o
   */
  public boolean equals(Object o) {
    if (! (o instanceof EmailHeadersPart)) {
      return false;
    }

    EmailHeadersPart part = (EmailHeadersPart) o;

    if (headersReference != null) {
      return (headersReference.equals(part.headersReference) && content.equals(part.content));
    } else {
      return (unstoredHeaders.equals(part.unstoredHeaders) && content.equals(part.content));
    }
  }
  
  
  public EmailHeadersPart(InputBuffer buf, Endpoint endpoint) throws IOException {
    super(buf);
    headersReference = new EmailDataReference(buf, endpoint);
    
    content = EmailContentPart.build(buf, endpoint, buf.readShort());
  }
  
  public void serialize(OutputBuffer buf) throws IOException {
    super.serialize(buf);
    headersReference.serialize(buf);
    
    buf.writeShort(content.getRawType());
    content.serialize(buf);
  }
  
  public short getRawType() {
    return TYPE; 
  }
}
