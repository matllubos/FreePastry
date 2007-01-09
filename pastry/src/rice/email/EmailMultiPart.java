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
import java.security.*;
import java.util.*;

import rice.*;
import rice.Continuation.*;
import rice.p2p.commonapi.Endpoint;
import rice.p2p.commonapi.rawserialization.*;
import rice.pastry.*;
import rice.post.storage.*;

/**
 * Represents the content of an email which is a multi-part entry
 *
 * @author Alan Mislove
 */
public class EmailMultiPart extends EmailContentPart {
  public static final short TYPE = 3;

  // serialver
  private static final long serialVersionUID = -1126503326536855181L;

  /**
   * The string used to seperate the parts of this multipart
   */
  public String type;

  /**
   * The actual content of this email part
   */
  public EmailHeadersPart[] content;

  /**
   * Constructor which takes in an Emailpart list 
   */
  public EmailMultiPart(EmailHeadersPart[] content, String type) {
    super(0);
    
    int size = 0;
    for (int i=0; i<content.length; i++) {
      size += content[i].getSize();
    }

    setSize(size);

    this.type = type;
    this.content = content;

    if ((content == null) || (content.length == 0)) {
      throw new IllegalArgumentException("Content[] must contain at least one element!");
    }
  }

  /**
   * Returns the seperator used for this multipart
   *
   * @return The seperator for this multipart
   */
  public String getType() {
    return type;
  }
  
  /**
   * This method returns a list of all the handles stored in this part
   * by adding them to the specified set.
   *
   * @param set The set to add the PastContentHandles to.
   */
  public void getContentHashReferences(Set set) {
    for (int i=0; i<content.length; i++)
      content[i].getContentHashReferences(set);
  }

  /**
   * Method which sets this part's storage service
   *
   * @param storage The local storage service
   */
  public void setStorage(StorageService storage) {
    super.setStorage(storage);

    for (int i=0; i<content.length; i++) {
      content[i].setStorage(storage);
    }
  }

  /**
   * Method which writes this part's content out to disk
   * and retrieves a reference to it.
   *
   * @param command The command to run once the data has been stored, and
   *   is returned the success or failure of this command
   */
  public void storeData(Continuation command) {
    content[0].storeData(new StandardContinuation(command) {
      private int i=0;
      
      public void receiveResult(Object o) {
        if (! (Boolean.TRUE.equals(o))) {
          parent.receiveResult(o);
          return;
        }

        i++;

        if (i < content.length) {
          content[i].storeData(this);
        } else {
          parent.receiveResult(new Boolean(true));
        }
      }
    });
  }

  /**
   * Method which retrieves and returns this content's EmailData
   *
   * @param command The command to run once the data is available
   */
  public void getContent(Continuation command) {
    command.receiveResult(content);
  }
  
  /**
   * Returns the hashCode
   *
   */
  public int hashCode() {
    int result = 293732638;
    
    for (int i=0; i<content.length; i++)
      result ^= content[i].hashCode();
    
    return result;
  }

  /**
   * Returns whether or not this EmailPart is equal to the object
   *
   * @return The equality of this and o
   */
  public boolean equals(Object o) {
    if (! (o instanceof EmailMultiPart))
      return false;

    EmailMultiPart part = (EmailMultiPart) o;

    return Arrays.equals(content, part.content);
  }
  

  public EmailMultiPart(InputBuffer buf, Endpoint endpoint) throws IOException {
    super(buf);
    type = buf.readUTF();
    
    content = new EmailHeadersPart[buf.readInt()];
    for (int i = 0; i < content.length; i++) {
      content[i] = (EmailHeadersPart)EmailContentPart.build(buf, endpoint, buf.readShort()); 
    }
  }
  
  public void serialize(OutputBuffer buf) throws IOException {
    super.serialize(buf);
    buf.writeUTF(type);
    
    buf.writeInt(content.length);
    for (int i = 0; i < content.length; i++) {
      buf.writeShort(content[i].getRawType());
      content[i].serialize(buf);
    }
  }
  
  public short getRawType() {
    return TYPE; 
  }
}
