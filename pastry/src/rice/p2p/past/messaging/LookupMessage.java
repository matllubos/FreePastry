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

package rice.p2p.past.messaging;

import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.past.*;

/**
 * @(#) LookupMessage.java
 *
 * This class is the representation of a lookup request (by Id) in Past.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 * @author Ansley Post
 * @author Peter Druschel
 */
public class LookupMessage extends ContinuationMessage {

  // the id to fetch
  private Id id;

  // whether or not this message has been cached
  private boolean cached = false;
   
  /**
   * Constructor which takes a unique integer Id, as well as the
   * data to be stored
   *
   * @param uid The unique id
   * @param id The location to be stored
   * @param source The source address
   * @param dest The destination address
   * @param content The data to be stored
   */
  public LookupMessage(int uid, Id id, Id source, Id dest) {
    super(uid, source, dest);

    this.id = id;
  }

  /**
   * Method which returns the id
   *
   * @param o The contained id
   */
  public Id getId() {
    return id;
  }

  /**
   * Returns whether or not this message has been cached
   *
   * @return Whether or not this message has been cached
   */
  public boolean isCached() {
    return cached;
  }

  /**
   * Sets this message as having been cached.
   */
  public void setCached() {
    cached = true;
  }
}

