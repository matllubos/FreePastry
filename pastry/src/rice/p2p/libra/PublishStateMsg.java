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
import rice.p2p.splitstream.*;
import rice.p2p.scribe.messaging.*;

/**
 * @(#) MyAnycastAckMessage.java Maintains the publish sequence number in the replica set of the root
 *
 */
public class PublishStateMsg implements Message {

    int seqNum;
    int topicNumber;
    String topicName;
    NodeHandle source;

    public void dump(ReplayBuffer buffer, PastryNode pn) {
	buffer.appendByte(rice.pastry.commonapi.PastryEndpointMessage.idLibraPublishStateMessage);
	buffer.appendInt(seqNum);
	buffer.appendInt(topicNumber);
	// The topicName is redundant information
	source.dump(buffer,pn);
	
    }

    


    public PublishStateMsg(ReplayBuffer buffer, PastryNode pn) {
	seqNum = buffer.getInteger();
	topicNumber = buffer.getInteger();
	topicName = "" + topicNumber;
	source = Verifier.restoreNodeHandle(buffer,pn);

    }




    public PublishStateMsg(int topicNumber, String topicName, int seqNum, NodeHandle source) {
	this.source = source;
	this.seqNum = seqNum;
	this.topicNumber = topicNumber;
	this.topicName = topicName;
    }

    public NodeHandle getSource() {
	return source;
    }
    

  public int getPriority() {
    return MEDIUM_HIGH_PRIORITY;
  }

  /**
   * Returns a String representation of this ack
   *
   * @return A String
   */
  public String toString() {
    return "PublishStateMsg "  + " source= " + source + " topicName= " + topicName + "seqNum= " + seqNum;
  }

}

