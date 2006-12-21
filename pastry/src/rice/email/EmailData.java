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

import rice.p2p.commonapi.*;
import rice.post.storage.*;

/**
 * Represents the attachment to an email.
 *
 * @author Alan Mislove
 */
public class EmailData implements PostData {
  
  // serialver
  private static final long serialVersionUID = 5751102504682081475L;

  /**
   * The data representing the stored data
   */
  protected transient byte[] _data;
  
  /**
   * Constructor. Takes in a byte[] representing the data of the
   * attachment
   *
   * @param data The byte[] representation
   */
  public EmailData(byte[] data) {
    _data = data;
  }

  /**
   * This method dynamically builds an appropriate HashReference for
   * this type of PostData given a location and key.  
   *
   * @param location the location of the data
   * @param key the key of the data
   */
  public ContentHashReference buildContentHashReference(Id[] location, byte[][] key) {
    return new EmailDataReference(location, key);
  }

  /**
   * This method dynamically builds an appropriate SignedReference for
   * this type of PostData given a location.  
   *
   * @param location the location of the data
   * @throws IllegalArgumentException Always
   */
  public SignedReference buildSignedReference(Id location) {
    throw new IllegalArgumentException("Email data is only stored as content-hash blocks.");
  }  

  /**
   * This method is not supported (you CAN NOT store an emaildata as a
   * secure block).
   *
   * @param location The location of the data
   * @param key The for the data
   * @throws IllegalArgumentException Always
   */
  public SecureReference buildSecureReference(Id location, byte[] key) {
    throw new IllegalArgumentException("Email data is only stored as content-hash blocks.");
  }  
  
  /**
   * Returns the data of this attachment
   *
   * @return The data stored in this attachment
   */
  public byte[] getData() {
    return _data;
  }

  /**
   * Returns whether or not this EmailData is equal to the object
   *
   * @return The equality of this and o
   */
  public boolean equals(Object o) {
    if (! (o instanceof EmailData))
      return false;

    return Arrays.equals(_data, ((EmailData) o).getData());
  }

  /**
   * Internal method for writing out this data object
   *
   * @param oos The current output stream
   */
  private void writeObject(ObjectOutputStream oos) throws IOException {
    oos.defaultWriteObject();
    
    oos.writeInt(_data.length);
    oos.write(_data);
  }
  
  /**
   * Internal method for reading in this data object
   *
   * @param ois The current input stream
   */
  private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
    ois.defaultReadObject();
    
    _data = new byte[ois.readInt()];
    ois.readFully(_data, 0, _data.length);
  }
}
