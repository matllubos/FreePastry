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

package rice.p2p.saar.blockbased;

import rice.p2p.saar.*;
//import rice.replay.*;
import rice.pastry.PastryNode;
import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.scribe.*;
import rice.p2p.scribe.messaging.*;
import java.util.*;

/**
 * @(#) MyAnycastAckMessage.java The ack for anycast message.
 *
 */
public class RespondBlocksMsg extends SaarDataplaneMessage {

    public static byte FALSE = 0;
    public static byte TRUE = 1;

    public static byte NULL = 2;
    public static byte NONNULL = 3;

    public Vector respondBlocks;


    //    public void dump(rice.replay.ReplayBuffer buffer, PastryNode pn) {
    //buffer.appendByte(rice.pastry.commonapi.PastryEndpointMessage.idLibraRespondBlocksMsg);
    //super.dump(buffer,pn);
    //buffer.appendShort(respondBlocks.size());
    //for(int i=0;i< respondBlocks.size();i++) {
    //    RequestTuple rTuple = (RequestTuple) respondBlocks.elementAt(i);
    //    rTuple.dump(buffer,pn);
    //}
    //}
    //
    //public RespondBlocksMsg(ReplayBuffer buffer, PastryNode pn) {
    //super(buffer,pn);
    //int respondBlocksLength = buffer.getShort();
    //respondBlocks = new Vector();
    //for(int i=0; i< respondBlocksLength; i++) {
    //    RequestTuple rTuple = new RequestTuple(buffer,pn);
    //    respondBlocks.add(rTuple);
    //}
    //}

    
    public RespondBlocksMsg(NodeHandle source, Topic topic, Vector respondBlocks) {
	super(source, topic);
	this.respondBlocks = respondBlocks;
	if(respondBlocks.size() > 1) {
	    System.out.println("ERROR: RespondBlocks contains multiple blocks");
	    System.exit(1);
	}
	
    }



    public String respondBlocksAsString() {
	String s = "";
	for(int i=0; i<respondBlocks.size();i++) {
	    RequestTuple rTuple = (RequestTuple) respondBlocks.elementAt(i);
	    s = s+ rTuple;
	}
	return s;
	    
    }


    public int getSizeInBytes() {
	int val; 
	val = 1 + (respondBlocks.size() * rice.p2p.saar.blockbased.BlockbasedClient.DATAMSGSIZEINBYTES);
	return val;
    }



  /**
   * Returns a String representation of this ack
   *
   * @return A String
   */
    public String toString() {
	return "RespondBlocksMsg: " + topic + " source= " + source + respondBlocksAsString();
    }

}

