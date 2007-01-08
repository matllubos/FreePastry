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

import rice.p2p.commonapi.rawserialization.InputBuffer;

/**
* Class which represents a "ping" message sent through the
* socket pastry system.
*
* @version $Id$
*
* @author Alan Mislove
*/
public class PingResponseMessage extends DatagramMessage {
      
  /**
   * 
   */
  private static final long serialVersionUID = -2835241938747821133L;
  public static final short TYPE = 9;

  /**
  * Constructor
  */
  public PingResponseMessage(/*SourceRoute outbound, SourceRoute inbound, */long start) {
    super(/*outbound, inbound, */start);
  }
  
  public PingResponseMessage(InputBuffer buf) throws IOException {
    super(buf);
  }

  public String toString() {
    return "PingResponseMessage";
  }

  public short getType() {
    return TYPE;
  }
}
