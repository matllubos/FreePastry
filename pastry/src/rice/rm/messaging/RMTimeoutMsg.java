

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
 * @(#) RMTimeoutMsg.java
 *
 * The timeout message containing a wrapped RMRequestKeysMsg, which is 
 * scheduled to be delivered at the local node TIMEOUT period after the 
 * wrapped RMRequestKeysMg in it was intially sent.
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
     * @param source the local node itself
     * @param address the RM application address
     * @param authorCred the credentials of the source
     * @param seqno for debugging purposes only
     * @param _wmsg the wrapped RMRequestKeysMsg
     */
    public RMTimeoutMsg(NodeHandle source, Address address,  Credentials authorCred, int seqno, RMRequestKeysMsg.WrappedMsg _wmsg) {
	super(source, address, authorCred, seqno);
	wmsg = _wmsg;
	
    }
    
    /**
     * If the RMResponseKeysMsg corresponding to the wrapped RMRequestKeysMsg
     * was received (done by having same 'eventId' field in RMRequestKeysMsg &
     * corresponding RMResponseKeysMsg) then nothing needs to be done. 
     * Otherwise, the wrapped RMRequestKeysMsg is resent, while incrementing
     * its attempt number. Note that after MAXATTEMPTS number of timeouts,
     * the message will be ignored even if not successfully delivered.
     */ 
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









