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

package rice.pastry.multiring;

import rice.pastry.*;

import java.util.*;

/**
 * This class represents a nodeId, combined with a ring-identfying Id.  This
 * class is designed to be backwards-compatible with other applications, as all
 * normal methods do what they would before.  Applications can use the
 * getRingId() methods in order to manipulate the ring identifier.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */

public class RingNodeId extends NodeId {

  private RingId ringId;
  
  public RingNodeId(byte material[], RingId ringId) {
    super(material);
    this.ringId = ringId;
  }

  public RingNodeId(int material[], RingId ringId) {
    super(material);
    this.ringId = ringId;
  }

  public RingNodeId(NodeId nodeId, RingId ringId) {
    super(nodeId.copy());
    this.ringId = ringId;
  }

  public RingNodeId(RingId ringId) {
    super();
    this.ringId = ringId;
  }

  public RingId getRingId() {
    return ringId;
  }

  public void setRingId(RingId ringId) {
    if (this.ringId == null) {
      this.ringId = ringId;
    }
  }

  public int hashCode() {
    return super.hashCode();
  }

  public boolean equals(Object o) {
    if (o instanceof RingNodeId) {
      RingNodeId other = (RingNodeId) o;

      return Arrays.equals(this.copy(), other.copy());
    }

    return false;
  }
  
  public String toString() {
    return "[RingId:" + ringId + ", NodeId" + super.toString() + "]";
  }
}