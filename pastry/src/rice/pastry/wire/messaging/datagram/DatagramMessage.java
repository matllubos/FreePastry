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

/**
 * Class which wraps all messages to be sent through the UDP-based
 * pastry protocol. Adds on a "packet number" which should be repeated
 * in the AcknowledgementMessage ack packet.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */
public abstract class DatagramMessage implements Serializable {

  // the "packet number"
  protected int num;

  /**
   * Builds a DatagramMessage given a packet number
   *
   * @param num The "packet number"
   */
  public DatagramMessage(int num) {
    this.num = num;
  }

  /**
   * Returns the "packet number" of this transmission
   *
   * @return The packet number of this message.
   */
  public int getNum() {
    return num;
  }
  
  /**
   * Sets the "packet number" of this transmission
   * 
   * @param num The packet number
   */
  public void setNum(int num) {
    this.num = num;
  }

  public String toString() {
    return "DatagramMsg num " + num;
  }
}