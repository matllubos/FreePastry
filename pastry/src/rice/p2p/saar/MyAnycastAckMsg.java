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

package rice.p2p.saar;

//import rice.replay.*;
import rice.pastry.PastryNode;
import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.scribe.*;
import rice.p2p.scribe.messaging.*;

/**
 * @(#) MyAnycastAckMessage.java The ack for anycast message.
 *
 */
public class MyAnycastAckMsg extends SaarDataplaneMessage {

    public static byte FALSE = 0;
    public static byte TRUE = 1;

    public static byte NULL = 2;
    public static byte NONNULL = 3;

    // This will contain the state of the anycast traversal etc
    public ScribeContent content;

//    public void dump(rice.replay.ReplayBuffer buffer, PastryNode pn) {
//	buffer.appendByte(rice.pastry.commonapi.PastryEndpointMessage.idLibraMyAnycastAckMessage);
//	super.dump(buffer,pn);
//	if(content == null) {
//	    buffer.appendByte(NULL);
//	} else {
//	    buffer.appendByte(NONNULL);
//	    content.dump(buffer,pn);
//	}
//
//    }
//
//    public MyAnycastAckMsg(ReplayBuffer buffer, PastryNode pn) {
//	super(buffer,pn);
//	if(buffer.getByte() == NULL) {
//	    content = null;
//	}else {
//	    content = Verifier.restoreScribeContent(buffer,pn);
//	}
//
//    }


    public MyAnycastAckMsg(ScribeContent content, NodeHandle source, Topic topic) {
	super(source, topic);
	this.content = content;
    }


    public ScribeContent getContent() {
	return content;
    }

    // todo: implement
//    public int getSizeInBytes() {
//	return content.getSizeInBytes();
//    }

  /**
   * Returns a String representation of this ack
   *
   * @return A String
   */
    public String toString() {
	SaarContent sContent = (SaarContent)content;
	SaarContent.NodeIndex[] traversedPath = sContent.getMsgPath();
	String pathString = sContent.pathAsString(traversedPath);
	return "MyAnycastAckMsg: " + topic + " source= " + source + " content= " + content + " TraversedPathString: " + pathString + " TraversedPathHops: " + (traversedPath.length - 1);	
    }

}

