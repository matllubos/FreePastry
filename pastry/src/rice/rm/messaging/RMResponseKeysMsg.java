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

import rice.rm.*;

import java.util.*;
import java.io.*;

/**
 * @(#) RMResponseKeysMsg.java
 *
 * A RM message. These messages are exchanged between the RM modules on the pastry nodes. 
 *
 * @version $Id$
 * @author Animesh Nandi
 */
public class RMResponseKeysMsg extends RMMessage implements Serializable{


    private Vector rangeSet;

    /**
     * Constructor : Builds a new RM Message
     */
    public RMResponseKeysMsg(NodeHandle source, Address address, Credentials authorCred, int seqno, Vector _rangeSet) {
	super(source,address, authorCred, seqno);
	rangeSet = _rangeSet;
    }



    /**
     * This method is called whenever the rm node receives a message for 
     * itself and wants to process it. The processing is delegated by rm 
     * to the message.
     * 
     */
    public void handleDeliverMessage( RMImpl rm) {
	//System.out.println(rm.getNodeId() + " received ResponseKeys msg from" + getSource().getNodeId());
	

	IdSet fetchSet = new IdSet();
	RMMessage.KEEntry entry;
	RMMessage.KEEntry toAskForEntry;

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
		    Id key = (Id)it.next();
		    Id ccw = rm.myRange.getCCW();
		    Id cw = rm.myRange.getCW();
		    if(key.isBetween(ccw, cw)) {
			fetchSet.addMember(key);
		    }
		    else {
			System.out.println("Warning: RMResponseKeysMsg has key not in the desired range");
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
		    rm.removePendingRange(getSource().getNodeId(), reqRange);
		}
		else {
		    rm.updatePendingRange(getSource().getNodeId(), reqRange, numKeys);

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
	    
	    //System.out.println("Before splitting");
	    //rm.printPendingRanges(getSource().getNodeId());
	    rm.splitPendingRanges(getSource().getNodeId());
	    //System.out.println("After splitting");
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
		    if((numKeys + totalNumKeys) <= RMMessage.MAXKEYSINRANGE) {
			totalNumKeys = totalNumKeys + numKeys;
			RMMessage.KEEntry toSendEntry = new RMMessage.KEEntry(pendingEntry.getReqRange(), false);
			toAskFor.add(toSendEntry);
		    }
		}
	    }
	    
	    //for(int k=0; k<toAskFor.size(); k++) {
	    //RMMessage.KEEntry checkEntry;
	    //checkEntry = (RMMessage.KEEntry)toAskFor.elementAt(k);
	    //System.out.println("entry[" + k + "] = " + checkEntry);
	    // }

    
	    RMRequestKeysMsg msg;
	    msg = new RMRequestKeysMsg(rm.getLocalHandle(),rm.getAddress() , rm.getCredentials(), rm.m_seqno ++, toAskFor);
	    rm.route(null, msg, getSource());
	    //System.out.println(rm.getNodeId() + "explicitly asking for keys");
	    
	
	}
	
    }
    
}



