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

package rice.pastry.wire.messaging.datagram;

import java.io.*;
import java.net.*;

/**
 * Class which represents an "ack" packet in the UDP version
 * of the pastry wire protocol.  Each DatagramMessage is assigned
 * a integer number (increasing), and each "ack" packet sent back 
 * contains that number.
 */
public class AcknowledgementMessage implements Serializable {

  // the address the ack in going to be sent to
  private InetSocketAddress address;

  // the "ack" number
  private int num;

  /**
   * Constructor.
   * 
   * @param address The destination of the "ack" packet
   * @param num The number of the original DatagramMessage.
   */
  public AcknowledgementMessage(InetSocketAddress address, int num) {
    this.address = address;
    this.num = num;
  }

  /** 
   * Returns the address of the destination of this ack message.
   * 
   * @return The destination address of the ack message.
   */
  public InetSocketAddress getAddress() {
    return address;
  }

  /**
   * Returns the number of this ack message.
   * 
   * @return The number of the original DatagramMessage.
   */
  public int getNum() {
    return num;
  }

  public String toString() {
    return "AckMsg to " + address + " num " + num;
  }
}