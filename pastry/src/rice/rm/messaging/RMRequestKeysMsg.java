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
import rice.pastry.messaging.*;
import rice.pastry.security.*;
import rice.pastry.routing.*;

import rice.rm.*;

import java.util.*;
import java.io.*;

/**
 * @(#) RMRequestKeysMsg.java
 *
 * This message is used to request for the set of keys lying in the ranges
 * specified in the message. This message coupled with the RMResponseKeysMsg 
 * is fundamental to the Keys Exchange Protocol of the Replica Manager. 
 * We handle loss of this type of message by a Timeout mechanism. 
 *
 * @version $Id$
 *
 * @author Animesh Nandi
 */
public class RMRequestKeysMsg extends RMMessage implements Serializable{

    /**
     * The list of ranges whose corresponding key sets the issuer of this 
     * message is interested in.
     */
    private Vector rangeSet;


    /**
     * This is to handle the loss of this type of messages.
     * (The other kind of messages do not require a Timeout mechanism,
     * hence they do not have this field).
     * Specifically, if a Timeout occurred for a RMRequestKeysMsg, then the
     * subsequent RMRequestKeysMsg that will be resent will have the SAME
     * eventId but an INCREASED value of seqNo. Also the RMResponseKeysMsg
     * sent in response to this message will have the SAME eventId.
     */
    private int eventId; 

    /**
     * In the Timeout mechanism upto MAXATTEMPTS will be made. 
     * This represents the number of attempts made so far.
     */
    private int attempt;

    /**
     * The timeout period within which if the response to this message
     * is not received, then the same message is resent.
     */
    public static int TIMEOUT = 10; // 10 seconds


    /**
     * The maximum number of attempts made in the timeout mechanism after
     * which the message is ignored.
     */
    public static int MAXATTEMPTS = 3; 

    /**
     * Contains the RMRequestKeysMsg and the destination to which it was
     * being sent.
     */
    public static class WrappedMsg {
	RMRequestKeysMsg msg;
	NodeHandle dest;
	

	public WrappedMsg(RMRequestKeysMsg _msg, NodeHandle _dest) {
	    msg = _msg;
	    dest = _dest;
	}

	public RMRequestKeysMsg getMsg() {
	    return msg;
	}

	public NodeHandle getDest() {
	    return dest;
	}
    } 





    /**
     * Constructor : Builds a new RM Message
     * @param source the source of the message
     * @param address the RM application address
     * @param authorCred the credentials of the source
     * @param seqno for debugging purposes only
     * @param _rangeSet the rangeSet of this message
     * @param _eventId the eventId of this message
     */
    public RMRequestKeysMsg(NodeHandle source, Address address, Credentials authorCred, int seqno, Vector _rangeSet, int _eventId) {
	super(source,address, authorCred, seqno);
	this.rangeSet = _rangeSet;
	this.eventId = _eventId;
	// This is the first attempt, Subsequent attempts due to Timeout
	// will call incrAttempt()
	this.attempt = 1;
    }



    /**
     * The handling of the message iterates of the 'rangeSet' as takes the
     * following action on the entries of type RMMessage.KEEntry.
     * If first takes the intersection of the requested range with the range
     * that it is itself responsible for. Then it issues a scan on this
     * intersected range. If the requestor asks only for the hash of the 
     * keys in the range, then only the hash is returned, otherwise the
     * key Set correspnding to the intersected range is returned.
     */
    public void handleDeliverMessage( RMImpl rm) {
	//System.out.println("At " + rm.getNodeId() + "received RequestKeysMsg from " + getSource().getNodeId() + " seqno= " + getSeqno());

	//for(int i=0; i< rangeSet.size(); i++) {
	//  RMMessage.KEEntry entry;
	//  entry = (RMMessage.KEEntry) rangeSet.elementAt(i);
	//  IdRange range = entry.getReqRange();
	//  System.out.println("At " + rm.getNodeId() + "e[" + i + "]=" + range);
	//	}

	Vector returnedRangeSet = new Vector();
	RMMessage.KEEntry entry;
	RMMessage.KEEntry returnedEntry;

	for(int i=0; i< rangeSet.size(); i++) {
	    entry = (RMMessage.KEEntry) rangeSet.elementAt(i);
	    IdRange reqRange;
	    IdRange iRange;
	    int numKeys = 0;
	    boolean hashEnabled;
	    Id hash = Id.build();
	    IdSet keySet = new IdSet();

	    // The values that will not be required to be filled
	    // will remain as the above DONTCARE values


	    reqRange = entry.getReqRange();
	    hashEnabled = entry.getHashEnabled();

	    //System.out.println("myRange= " + rm.myRange);
	    //System.out.println("reqRange= " + reqRange);
	    if(rm.myRange == null) 
		iRange = reqRange;
	    else
		iRange = reqRange.intersect(rm.myRange);
	    //System.out.println("iRange= " + iRange);
	    if(iRange.isEmpty()) {
		// To notify the requestor that no keys were 
		// found in this range
		numKeys = 0;
	    }
	    else {
		keySet = rm.app.scan(iRange);
		numKeys = keySet.numElements();
	    }
	    if(hashEnabled ) {
		hash = keySet.getHash();
	    }
	    returnedEntry = new RMMessage.KEEntry(reqRange, iRange, numKeys, hashEnabled, hash, keySet);
	    returnedRangeSet.add(returnedEntry);

	}
	
	RMResponseKeysMsg msg;
	msg = new RMResponseKeysMsg(rm.getLocalHandle(),rm.getAddress() , rm.getCredentials(), rm.m_seqno ++, returnedRangeSet, getEventId());
	rm.route(null, msg, getSource());
    }

    
    public int getEventId() {
	return eventId;
    }

    /**
     * Since this message has a timeout mechanism, the next time we resend
     * the message as a result of Timeout we need to reflect the current
     * seqNo used by the application in the message.
     */
    public void setSeqNo(int val) {
	_seqno = val;

    }


    /**
     * Since this message has a timeout mechanism, the next time we resend
     * the message as a result of Timeout we need to increment the attempt
     * number.
     */
    public void incrAttempt() {
	attempt++;
	return;
    }
 
    public int getAttempt() {
	return attempt;
    }
    
}





