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
package rice.post.storage;

import java.io.*;
import java.security.*;

import rice.p2p.commonapi.*;
import rice.p2p.past.*;

/**
* This interface is designed to serve as an abstraction of a
 * data object stored in Post.  This object will be stored
 * in an encrypted state at a location in the network.  Users
 * can access this object by having a copy of the corresponding
 * Reference object, which contains the location and
 * possibly the key of this object.
 * 
 * @version $Id$
 */
public class GroupData implements PostData {
  public static final short TYPE = 3;

  // The list of Postdata objects we are storing
  protected PostData[] data;
  
  /**
   * Builds a GroupData given a collection of PostData
   *
   * @param data The data
   */
  public GroupData(PostData[] data) {
    this.data = data;
  }
  
  /**
   * Returns the data
   *
   * @returns the data
   */
  public PostData[] getData() {
    return data;
  }
  
  /**
  * This method dynamically builds an appropriate SignedReference
   * for this type of PostData given a location.
   *
   * @param location The location of the data
   * @return A pointer to the data
   */
  public SignedReference buildSignedReference(Id location) {
    return new SignedReference(location);
  }
  
  /**
  * This method dynamically builds an appropriate ContentHashReference
   * for this type of PostData given a location and key.
   *
   * @param location The location of the data
   * @param key The for the data
   * @return A pointer to the data
   */
  public ContentHashReference buildContentHashReference(Id[] location, byte[][] key) {
    throw new UnsupportedOperationException("GroupData cannot be stored as a ContentHash");
  }
  
  /**
    * This method dynamically builds an appropriate SecureReference
   * for this type of PostData given a location and key.
   *
   * @param location The location of the data
   * @param key The for the data
   * @return A pointer to the data
   */
  public SecureReference buildSecureReference(Id location, byte[] key) {
    throw new UnsupportedOperationException("GroupData cannot be stored as a Secure");
  }
}
