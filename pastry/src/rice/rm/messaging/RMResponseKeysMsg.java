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
import rice.pastry.dist.*;
import rice.rm.*;

import java.util.*;
import java.io.*;

/**
 * @(#) RMResponseKeysMsg.java
 *
 * This message is delivered in response to a RMRequestKeysMsg. It shares
 * the same 'eventId' as the RMRequestKeysMsg to which it responded.
 *
 * @version $Id$
 *
 * @author Animesh Nandi
 */
public class RMResponseKeysMsg extends RMMessage implements Serializable{

    /**
     * The list of ranges whose corresponding key sets the issuer of its 
     * corresponding RMRequestKeysMsg message was interested in, except with
     * the difference that the ranges in this list are a result of the 
     * intersection of the ranges requested for and 'myRange' of the responder.
     */
    private Vector rangeSet;

    /**
     * Event Id of the RMRequestKeysMsg to which this is a response.
     */
    private int eventId;

    /**
     * Constructor : Builds a new RM Message
     * @param source the source of the message
     * @param address the RM application address
     * @param authorCred the credentials of the source
     * @param seqno for debugging purposes only
     * @param _rangeSet the rangeSet of this message
     * @param _eventId the eventId of this message
     */
    public RMResponseKeysMsg(NodeHandle source, Address address, Credentials authorCred, int seqno, Vector _rangeSet, int _eventId) {
	super(source,address, authorCred, seqno);
	rangeSet = _rangeSet;
	eventId = _eventId;
    }



    /**
     * The handling of the message does the following -
     *
     * 1. Removes the event characterized by 'eventId' from the m_PendingEvents
     *    hashtable signifying that a response to the message was received 
     *    to ensure that on the occurrence of the timeout, the message is NOT
     *    resent.
     *
     * 2. We then iterate over the 'rangeSet' for each entry of type 
     *    RMMessage.KEEntry we do the following:
     *    a) If the entire key set for the rangge was requested, it notifies 
     *       the RMClient to fetch() the keys in that set. Additionally, it
     *       removes this range from the list of pending ranges in the 
     *       m_pendingRanges hashtable.
     *    b) If only the hash of the keys in the range was requested and 
     *       the hash matched then we
     *       remove this range from the list of pending ranges in the 
     *       m_pendingRanges hashtable.
     *    c) If only the hash of the keys in the range was requested and
     *       the hash did not match, then we update the entry corresponding
     *       to this range in the pending ranges list
     *       with the number of keys in this range as notified by the source
     *       of the message.
     *
     * 3. We now iterate over the pending Ranges list and split the ranges 
     *    whose expected number of keys('numKeys') is greater than 
     *    MAXKEYSINRANGE. The splitting method is recursive binary spliting
     *    until we get a total SPLITFACTOR number of subranges from the
     *    intitial range.
     *
     * 4. At this point all ranges in the pendingRanges list have either a
     *    value of 'numKeys' less than MAXKEYSINRANGE or a value or '-1' 
     *    denoting uninitialized. 
     *
     * 5. Now we iterate over this list of pending ranges and build a new
     *    RMRequestKeysMsg with a new rangeSet called 'toAskFor' in the code
     *    below. All add all ranges with uninitialized value of 'numKeys' 
     *    to the new list 'toAskFor' setting their 'hashEnabled' field in 
     *    their corresponding RMMessage.KEEntry to 'true', signifying that
     *    it is interested only in the hash value of the keys in this range.
     *    Additonally, it also adds ranges with already initialized 'numKeys'
     *    values to this 'toAskFor' list with the 'hashEnabled' field set to
     *    'false' as long as the total size of the key sets corresponding to
     *    the entries in  'toAskFor' is less than MAXKEYSINRANGE.
     *
     * 6. Sends the new RMRequestKeysMsg with this 'toAskFor' list. 
     *    Additionally, in order to implement the TIMEOUT mechanism to
     *    handle loss of RMRequestKeysMsg, we wrap the RMRequestKeysMsg
     *    in a RMTimeoutMsg which we schedule on the local node after a 
     *    TIMEOUT period.     
     */
    public void handleDeliverMessage( RMImpl rm) {
	//System.out.println("Start " + rm.getNodeId() + " received ResponseKeys msg from" + getSource().getNodeId() + "seq= " + getSeqno());


	// We will first remove this event from the m_PendingEvents Hashtable
	int eId = eventId;
	NodeId toNode = getSource().getNodeId();
	rm.removePendingEvent(toNode, eId);

	IdSet fetchSet = new IdSet();
	RMMessage.KEEntry entry;
	RMMessage.KEEntry toAskForEntry;

	//for(int i=0; i< rangeSet.size(); i++) {
	//  entry = (RMMessage.KEEntry) rangeSet.elementAt(i);
	//  System.out.println("At " + rm.getNodeId() + "e[" + i + "]=" + entry);
	//}


	for(int i=0; i< rangeSet.size(); i++) {
	    entry = (RMMessage.KEEntry) rangeSet.elementAt(i);
	    IdRange reqRange = entry.getReqRange();
	    IdRange iRange = entry.getRange();
	    int numKeys = entry.getNumKeys();
	    boolean hashEnabled = entry.getHashEnabled();
	    Id hash = entry.getHash();
	    IdSet keySet = entry.getKeySet();

	    if(numKeys == 0) {
		rm.removePendingRange(getSource().getNodeId(), reqRange);
		continue;
	    }
	    if(!hashEnabled) {
		
		// We simple add these keys to the fetchSet
		Iterator it = keySet.getIterator();
		while(it.hasNext()) {
		    if(rm.myRange!=null) {
			Id key = (Id)it.next();
			if(rm.myRange.contains(key)) {
			    fetchSet.addMember(key);
			}
			else {
			    //System.out.println("Warning: Possible in Distributed Only due to race conditions : RMResponseKeysMsg has key not in the desired range");
			}
		    }
		}

		rm.removePendingRange(getSource().getNodeId(), reqRange);
		continue;
	    }
	    else {
		Id oHash , myHash;
		oHash = hash;
		//System.out.println("oHash= " + oHash);
		IdSet myKeySet = rm.app.scan(iRange);
		myHash = myKeySet.getHash();
		if(oHash.equals(myHash)) {
		    //System.out.print(" Hash matched ");
		    rm.removePendingRange(getSource().getNodeId(), reqRange);
		    continue;
		}
		else {
		    rm.updatePendingRange(getSource().getNodeId(), reqRange, numKeys);
		    continue;

		}
	    }
	}
	if(fetchSet.numElements()!= 0)
	    rm.app.fetch(fetchSet);		
		
	
	if(rm.m_pendingRanges.containsKey(getSource().getNodeId())) {

	    // This vector starts a new round of ranges that this node requests
	    Vector toAskFor = new Vector();
	    int totalNumKeys = 0;

	    // This means that we have some Ranges that still
	    // need to be dealt with
		    
	    // We will now check the state of the pending Ranges
	    // Do Splitting of Ranges for those ranges that exceed
	    // 'numKeys' value over the threshold
	    
	    //System.out.println("At " + rm.getNodeId() + " Before splitting");
	    //rm.printPendingRanges(getSource().getNodeId());
	    rm.splitPendingRanges(getSource().getNodeId());
	    //System.out.println("At " + rm.getNodeId() + "After splitting");
	    //rm.printPendingRanges(getSource().getNodeId());
	    Vector pendingRanges;
	    pendingRanges = rm.getPendingRanges(getSource().getNodeId());
	    for(int j=0; j< pendingRanges.size(); j++) {
		RMImpl.KEPenEntry pendingEntry = (RMImpl.KEPenEntry)pendingRanges.elementAt(j);
		// At this instant we have already done the splitting
		// So the 'numKeys' in the Pending Ranges will
		// either be -1 or will be less than the threshold
		
		if(pendingEntry.getNumKeys() == -1) {
		    RMMessage.KEEntry toSendEntry = new RMMessage.KEEntry(pendingEntry.getReqRange(), true);
		    toAskFor.add(toSendEntry);
		}
		else {
		    int numKeys;
		    numKeys = pendingEntry.getNumKeys();
		    //System.out.println("numKeys= " + numKeys);
		    if((numKeys + totalNumKeys) <= RMImpl.MAXKEYSINRANGE) {
			totalNumKeys = totalNumKeys + numKeys;
			RMMessage.KEEntry toSendEntry = new RMMessage.KEEntry(pendingEntry.getReqRange(), false);
			toAskFor.add(toSendEntry);
		    }
		}
	    }
	    
    
	    RMRequestKeysMsg msg;
	    int neweId = rm.m_eId ++;
	    msg = new RMRequestKeysMsg(rm.getLocalHandle(),rm.getAddress() , rm.getCredentials(), rm.m_seqno ++, toAskFor, neweId);

	    if(rm.getPastryNode() instanceof DistPastryNode) {
		// We will also wrap this message in order to implement the TIMEOUT mechanism.
		RMRequestKeysMsg.WrappedMsg wmsg = new RMRequestKeysMsg.WrappedMsg(msg, getSource());
		RMTimeoutMsg tmsg = new RMTimeoutMsg(rm.getNodeHandle(), rm.getAddress(), rm.getCredentials(), rm.m_seqno ++, wmsg);
		rm.getPastryNode().scheduleMsg(tmsg, RMRequestKeysMsg.TIMEOUT * 1000);
		rm.addPendingEvent(getSource().getNodeId(), neweId); 
		
	    }

	    rm.route(null, msg, getSource());
	    //System.out.println(rm.getNodeId() + "explicitly asking for keys");
	    
	
	}
	//System.out.println("Done " + rm.getNodeId() + " received ResponseKeys msg from" + getSource().getNodeId() + "seq= " + getSeqno());
    }

     
    public int getEventId() {
	return eventId;
    }

    
}




