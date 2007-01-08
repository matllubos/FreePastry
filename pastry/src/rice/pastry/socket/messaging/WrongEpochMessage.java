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

package rice.pastry.socket.messaging;

import java.io.*;

import rice.p2p.commonapi.rawserialization.*;
import rice.pastry.socket.*;

/**
 * Class which represents a "ping" message sent through the
 * socket pastry system.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */
public class WrongEpochMessage extends DatagramMessage {
  
  static final long serialVersionUID = 2838948342952784682L;

  public static final short TYPE = 14;
  
  protected EpochInetSocketAddress incorrect;
  protected EpochInetSocketAddress correct;
  
  /**
  * Constructor
   */
  public WrongEpochMessage(/*SourceRoute outbound, SourceRoute inbound, */EpochInetSocketAddress incorrect, EpochInetSocketAddress correct, long start) {
    super(/*outbound, inbound,*/ start);
    
    this.incorrect = incorrect;
    this.correct = correct;
  }
  
  public WrongEpochMessage(InputBuffer buf) throws IOException {
    super(buf);
    incorrect = EpochInetSocketAddress.build(buf);
    correct = EpochInetSocketAddress.build(buf);
  }

  public EpochInetSocketAddress getIncorrect() {
    return incorrect;
  }
  
  public EpochInetSocketAddress getCorrect() {
    return correct;
  }

  public String toString() {
    return "WrongEpochMessage";
  }

  public short getType() {
    return TYPE;        
  }

  public void serialize(OutputBuffer buf) throws IOException {
    super.serialize(buf);
    incorrect.serialize(buf);
    correct.serialize(buf);
  }
}
