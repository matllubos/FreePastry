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

import java.io.*;
import java.security.*;
import java.util.*;

import rice.*;
import rice.Continuation.*;
import rice.p2p.commonapi.Endpoint;
import rice.p2p.commonapi.rawserialization.*;
import rice.pastry.*;
import rice.post.storage.*;

/**
 * Represents the content of an email
 *
 * @author Alan Mislove
 */
public abstract class EmailContentPart implements Serializable {
  
  // serialversionuid for backwarnds compatibility
  private static final long serialVersionUID = 6448441928180861395L;

  /**
   * The size of this part, in bytes
   */
  protected int size;
  
  /**
   * The local storage service
   */
  protected transient StorageService storage;

  /**
   * Constructor which takes in an EmailData
   */
  public EmailContentPart(int size) {
    this.size = size;
  }

  /**
   * Sets the size of this part, in bytes
   *
   * @param size The size of this part
   */
  protected void setSize(int size) {
    this.size = size;
  }

  /**
   * Returns the size of this part, in bytes
   *
   * @return The size of this part
   */
  public int getSize() {
    return size;
  }
  
  /**
   * Method which sets this part's storage service
   *
   * @param storage The local storage service
   */
  public void setStorage(StorageService storage) {
    this.storage = storage;
  }
  
  /**
   * Method which writes this part's content out to disk
   * and retrieves a reference to it.
   *
   * @param command The command to run once the data has been stored, and
   *   is returned the success or failure of this command
   */
  public abstract void storeData(Continuation command);

  /**
   * Method which retrieves and returns this content's EmailData
   *
   * @param command The command to run once the data is available
   */
  public abstract void getContent(Continuation command);

  /**
   * Overridden to enforce subclasses have a valid equals
   */
  public abstract boolean equals(Object o);
  
  /**
   * Overridden to enforce hashCode();
   */
  public abstract int hashCode();
  
  /**
   * This method returns a list of all the handles stored in this part
   * by adding them to the specified set.
   *
   * @param set The set to add the PastContentHandles to.
   */
  public abstract void getContentHashReferences(Set set);
  
  public EmailContentPart(InputBuffer buf) throws IOException {
    size = buf.readInt(); 
  }
  
  public void serialize(OutputBuffer buf) throws IOException {
    buf.writeInt(size);
  }

  public abstract short getRawType();

  public static EmailContentPart build(InputBuffer buf, Endpoint endpoint, short type) throws IOException {
    switch(type) {
      case EmailHeadersPart.TYPE:
        return new EmailHeadersPart(buf, endpoint);
      case EmailMessagePart.TYPE:
        return new EmailMessagePart(buf, endpoint);
      case EmailMultiPart.TYPE:
        return new EmailMultiPart(buf, endpoint);
      case EmailSinglePart.TYPE:
        return new EmailSinglePart(buf, endpoint);        
    }
    throw new IllegalArgumentException("Unknown type:"+type);
  }
}
