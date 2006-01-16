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
import java.util.*;

/**
 * This message gets back the current root for the topic, it helps in implementing centralized algorithms, where 
 * the root maintains all state for the centralized algorithm
 */
public class RequestRootHandleMsg extends ScribeMessage {

    public String topicName;

    public NodeHandle lastInPath = null; // This avoids adding the local node twice due to the artifact of forwardMsg() being called twice


    // This is set to true by the  root and the source of the message acepts it after that
    public boolean acceptNow = false;


    // These will be the code for read/writes for the msg at the socket layer
    public static byte WRITE = 1;
    public static byte READ = 2;
    

    public Vector debugInfo;

    public NodeHandle initialRequestor;

    // This will store the path information
    public MyScribeContent content;

    
    public class DebugInfo {
	int plIndex; // Node at which it was read/written
	String type; // READ/WRITE
	long time; // system time at that node



	public void dump(rice.replay.ReplayBuffer buffer, PastryNode pn) {
	    buffer.appendShort(plIndex);
	    if(type.equals("WRITE")) {
		buffer.appendByte(WRITE);
	    } else {
		buffer.appendByte(READ);
	    }
	    buffer.appendLong(time);
	}

	public DebugInfo(ReplayBuffer buffer, PastryNode pn) {
	    plIndex = buffer.getShort();
	    if(buffer.getByte() == WRITE) {
		type = "WRITE";
	    } else {
		type = "READ";
	    }
	    time = buffer.getLong();
	}

	public DebugInfo(int plIndex, String type, long time) {
	    this.plIndex = plIndex;
	    this.type = type;
	    this.time = time;
	}

	public String toString() {
	    String s = "[ ";
	    s = s + plIndex + " " + type + " " + time;
	    s = s + "]";
	    return s;

	}
	    
    }


    public void dump(rice.replay.ReplayBuffer buffer, PastryNode pn) {
	buffer.appendByte(rice.pastry.commonapi.PastryEndpointMessage.idLibraRequestRootHandleMsg);
	super.dump(buffer,pn);
	initialRequestor.dump(buffer,pn);
	buffer.appendShort(topicName.length());
	buffer.appendBytes(topicName.getBytes());
	content.dump(buffer,pn);
	if(acceptNow) {
	    buffer.appendByte(Verifier.TRUE);
	} else {
	    buffer.appendByte(Verifier.FALSE);
	}

	buffer.appendShort(debugInfo.size());
	for(int i=0; i<debugInfo.size(); i++) {
	    DebugInfo info = (DebugInfo)debugInfo.elementAt(i);
	    info.dump(buffer,pn);
	}
	if(lastInPath == null) {
	    buffer.appendByte(Verifier.NULL);
	} else {
	    buffer.appendByte(Verifier.NONNULL);
	    lastInPath.dump(buffer,pn);
	}
	
	
    }


    public RequestRootHandleMsg(ReplayBuffer buffer, PastryNode pn) {
	super(buffer,pn);
	initialRequestor = Verifier.restoreNodeHandle(buffer,pn);
	int length = buffer.getShort();
	this.topicName = new String(buffer.getByteArray(length));
	content = (MyScribeContent)Verifier.restoreScribeContent(buffer,pn);
	if(buffer.getByte() == Verifier.TRUE) {
	    acceptNow = true;
	} else {
	    acceptNow = false;
	}
	int debugInfoLength = buffer.getShort();
	this.debugInfo = new Vector();
	for(int i=0; i< debugInfoLength; i++) {
	    DebugInfo info = new DebugInfo(buffer,pn);
	    debugInfo.add(info);
	}
	byte nullVal = buffer.getByte();
	if(nullVal == Verifier.NULL) {
	    lastInPath = null;
	}else {
	    lastInPath = Verifier.restoreNodeHandle(buffer,pn);
	}

    }


    
    public RequestRootHandleMsg(String topicName, Topic topic, NodeHandle source) {
	super(source, topic);
	this.initialRequestor = source;
	this.topicName = topicName;
	this.debugInfo = new Vector();
	// The topic Name is just a dummy and will not be used
	this.content = new MyScribeContent("0", this.initialRequestor, 0, true, null, 0, null);  
	
    }


    public NodeHandle getInitialRequestor() {
	return initialRequestor;
    }


    public String debugInfoAsString() {
	String s = "DebugString:";
	for(int i=0; i< debugInfo.size(); i++) {
	    DebugInfo info = (DebugInfo)debugInfo.elementAt(i);
	    s = s + info;
	}
	return s;	

    }

    public MyScribeContent getContent() {
	return content;
    }


    public void appendTraversedTime(int plIndex, String type, long time) {
	debugInfo.add(new DebugInfo(plIndex,type,time));
    }
    



  /**
   * Returns a String representation of this ack
   *
   * @return A String
   */
  public String toString() {
      return "RequestRootHandleMsg " + topic + " source= " + source + " topicName= " + topicName;  }

}

