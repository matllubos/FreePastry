/**************************************************************************

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

package rice.pastry.wire;

import java.net.*;

/**
 * Wrapper class which contains an object to write and the address
 * it needs to be written to.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */
public class PendingWrite {

  // the destination address
  private InetSocketAddress address;

  // the object to write
  private Object o;

  /**
   * Contructs a PendingWrite from an address and an object
   *
   * @param address The destination address of this object
   * @param o The object to be written.
   */
  public PendingWrite(InetSocketAddress address, Object o) {
    this.address = address;
    this.o = o;
  }

  /**
   * Returns the destination address of this write
   *
   * @return The destination address of this pending write.
   */
  public InetSocketAddress getAddress() {
    return address;
  }

  /**
   * Returns the object to be written
   *
   * @return The object to be written
   */
  public Object getObject() {
    return o;
  }
}
