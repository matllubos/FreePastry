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

package rice.p2p.libra;

import rice.replay.*;
import rice.pastry.PastryNode;
import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.scribe.*;
import rice.p2p.scribe.messaging.*;

/**
 * This message gets back the current root for the topic, it helps in implementing centralized algorithms, where 
 * the root maintains all state for the centralized algorithm
 */
public class RootHandleAckMsg extends ScribeMessage {

    String topicName;

    public void dump(rice.replay.ReplayBuffer buffer, PastryNode pn) {
	buffer.appendByte(rice.pastry.commonapi.PastryEndpointMessage.idLibraRootHandleAckMsg);
	super.dump(buffer,pn);
	buffer.appendShort(topicName.length());
	buffer.appendBytes(topicName.getBytes());
    }
    
    public RootHandleAckMsg(ReplayBuffer buffer, PastryNode pn) {
	super(buffer,pn);
	int length = buffer.getShort();
	this.topicName = new String(buffer.getByteArray(length));
    }
    
    
    public RootHandleAckMsg(String topicName, Topic topic, NodeHandle source) {
	super(source, topic);
	this.topicName = topicName;
    }

  /**
   * Returns a String representation of this ack
   *
   * @return A String
   */
  public String toString() {
      return "RootHandleAckMsg " + topic + " source= " + source + " topicName= " + topicName;  }

}

