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
 * @(#) MyAnycastAckMessage.java The ack for anycast message.
 *
 */
public class MyAnycastAckMsg extends ScribeMessage {

    public static byte FALSE = 0;
    public static byte TRUE = 1;

    public static byte NULL = 2;
    public static byte NONNULL = 3;

    // This will contain the state of the anycast traversal etc
    MyScribeContent content;

    byte[] responderIp = new byte[4];
    byte[] responderESMId= new byte[4];
    int responderESMPort;
    GNPCoordinate responderGNPCoord = null;

 
    public void dump(rice.replay.ReplayBuffer buffer, PastryNode pn) {
	buffer.appendByte(rice.pastry.commonapi.PastryEndpointMessage.idLibraMyAnycastAckMessage);
	super.dump(buffer,pn);
	content.dump(buffer,pn);
	buffer.appendBytes(responderIp);
	buffer.appendBytes(responderESMId);
	buffer.appendInt(responderESMPort);
	if(responderGNPCoord == null) {
	    buffer.appendByte(NULL);
	} else {
	    buffer.appendByte(NONNULL);
	    responderGNPCoord.dump(buffer,pn);
	}
    }

    public MyAnycastAckMsg(ReplayBuffer buffer, PastryNode pn) {
	super(buffer,pn);
	content = (MyScribeContent)Verifier.restoreScribeContent(buffer,pn);
	responderIp = buffer.getByteArray(4);
	responderESMId = buffer.getByteArray(4);
	responderESMPort = buffer.getInteger();
	if(buffer.getByte() == NULL) {
	    responderGNPCoord = null;
	}else {
	    responderGNPCoord = new GNPCoordinate(buffer, pn);
	}
	//System.out.println("Constructed MyAnycastAckMessage: " + this);
    }


    public MyAnycastAckMsg(MyScribeContent content, NodeHandle source, Topic topic, byte[] responderIp, byte[] responderESMId, int responderESMPort, GNPCoordinate responderGNPCoord) {
	super(source, topic);
	this.content = content;
	for(int i=0; i<4; i++) {
	    this.responderIp[i] = responderIp[i];
	}
	for(int i=0; i<4; i++) {
	    this.responderESMId[i] = responderESMId[i];
	}
	this.responderESMPort = responderESMPort;
	if(responderGNPCoord != null) {
	    this.responderGNPCoord = new GNPCoordinate(responderGNPCoord);
	}
	
    }

    public byte[] getResponderIp() {
	return responderIp;
    }
    
    
    public byte[] getResponderESMId() {
	return responderESMId;
    }
    
    public int getResponderESMPort() {
	return responderESMPort;
    }


    public GNPCoordinate getResponderGNPCoord() {
	return responderGNPCoord;
    }


    public MyScribeContent getContent() {
	return content;
    }

  /**
   * Returns a String representation of this ack
   *
   * @return A String
   */
    public String toString() {
	return "MyAnycastAckMsg: " + topic + " source= " + source + " content= " + content;
    }

}

