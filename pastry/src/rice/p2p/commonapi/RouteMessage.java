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

package rice.p2p.commonapi;

import java.io.*;

/**
 * This class is a container class which represents a message, as it is
 * about to be forwarded to another node.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 * @author Peter Druschel
 */
public abstract class RouteMessage implements Serializable {

  protected Id id;

  protected NodeHandle nextHop;

  protected Message message;

  /**
   * Constructor which takes an id, nextHop, and internal message.  This is
   * protected as only common API implementations should actually create
   * route messages.
   *
   * @param id The destination id.
   * @param nextHop The node that will receive this message next
   * @param message The internal message.
   */
  protected RouteMessage(Id id, NodeHandle nextHop, Message message) {
    this.id = id;
    this.nextHop = nextHop;
    this.message = message;
  }

  public Id getDestination() {
    return id;
  }

  public NodeHandle getNextHop() {
    return nextHop;
  }

  public Message getMessage() {
    return message;
  }

  public void setDestination(Id id) {
    this.id = id;
  }

  public void setNextHop(NodeHandle nextHop) {
    this.nextHop = nextHop;
  }

  public void setMessage(Message message) {
    this.message = message;
  }
  
}


