/*************************************************************************

"Free Pastry" Peer-to-Peer Application Development Substrate

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

package rice.caching.messaging;

import rice.pastry.*;
import rice.pastry.messaging.*;
import rice.pastry.security.*;

import java.io.*;
import java.util.*;

/**
 * This abstract class is designed to be extended by applications which use
 * the dynmaic caching functinoality.  Only the effective "lookup" messages
 * need to extend this class.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */
public abstract class CacheLookupMessage extends Message implements Serializable {

  // a list containing all of the hops of this message
  private Vector hops = new Vector();

  // a transient local pastry node
  private transient PastryNode node;

  /**
   * Constructor to comply with the Message class.
   */
  public CacheLookupMessage(Address dest) {
    super(dest);
  }

  /**
   * Constructor to comply with the Message class.
   */
  public CacheLookupMessage(Address dest, Credentials cred) {
    super(dest, cred);
  }

  /**
   * Constructor to comply with the Message class.
   */
  public CacheLookupMessage(Address dest, Credentials cred, Date timestamp) {
    super(dest, cred, timestamp);
  }

  /**
   * Constructor to comply with the Message class.
   */
  public CacheLookupMessage(Address dest, Date timestamp) {
    super(dest, timestamp);
  }

  /**
   * Method which returns the handle of the previous node which this lookup message
   * was on.  If no previous node exists, then null is returned.
   *
   * @return The previous node which this handle was on.
   */
  public NodeHandle[] getHops() {
    NodeHandle[] result = new NodeHandle[hops.size()];

    for (int i=0; i<hops.size(); i++) {
      result[i] = (NodeHandle) hops.elementAt(i);
    }

    return result;
  }

  /**
   * Method by which a hop is added to the message
   *
   * @param handle The current handle
   */
  public void addHop(NodeHandle handle) {
    hops.addElement(handle);
  }
}





