/*************************************************************************

"Free Pastry" Peer-to-Peer Application Development Substrate 

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


package rice.rm.messaging;

import rice.pastry.*;
import rice.pastry.security.*;
import rice.pastry.routing.*;
import rice.pastry.messaging.*;

import rice.rm.*;
import rice.rm.messaging.*;

import java.io.*;
import java.util.*;

/**
 *
 * 
 * @version $Id$ 
 * 
 * @author Animesh Nandi
 */


public class RMTimeoutMsg extends RMMessage implements Serializable
{

    RMRequestKeysMsg.WrappedMsg wmsg;

    /**
     * Constructor
     */
    public RMTimeoutMsg(NodeHandle source, Address address,  Credentials authorCred, int seqno, RMRequestKeysMsg.WrappedMsg _wmsg) {
	super(source, address, authorCred, seqno);
	wmsg = _wmsg;
	
    }
    
    public void handleDeliverMessage( RMImpl rm) {
	//System.out.println("");
	//System.out.println("RMTimeout message: at " + rm.getNodeId());
	RMRequestKeysMsg msg = wmsg.getMsg();
	NodeHandle dest = wmsg.getDest();
	int eId = msg.getEventId();
	if(rm.isPendingEvent(dest.getNodeId(), eId)) {
	    if(msg.getAttempt() < RMRequestKeysMsg.MAXATTEMPTS) {
		// This means that we need to resend the message
		// We will first update the seqNo field of the msg;
		msg.incrAttempt();
		msg.setSeqNo(rm.m_seqno++);
		rm.route(null, msg, dest);
		//System.out.println("Resending the RMRequestKeysMsg at Timeout");
		
		RMTimeoutMsg tmsg = new RMTimeoutMsg(rm.getNodeHandle(), rm.getAddress(), rm.getCredentials(), rm.m_seqno ++, wmsg);
		rm.getPastryNode().scheduleMsg(tmsg, RMRequestKeysMsg.TIMEOUT * 1000);
	    }
	    else {
		//System.out.println("MAXATTEMPTS occurred");
		// We do not try more than MAXATTEMPTS
		rm.removePendingEvent(dest.getNodeId(), eId);
	    }
	    
	}
	else {
	    //System.out.println("We already received a Response");
	}
    }


    public String toString() {
	return new String( "TIMEOUT_MSG:" );
    }
}









