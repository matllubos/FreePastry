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
 * A RM message. These messages are exchanged between the RM modules on the pastry nodes. 
 *
 * @version $Id$
 * @author Animesh Nandi
 */
public class RMRequestKeysMsg extends RMMessage implements Serializable{

    private Vector rangeSet;


    /**
     * Constructor : Builds a new RM Message
     */
    public RMRequestKeysMsg(NodeHandle source, Address address, Credentials authorCred, int seqno, Vector _rangeSet) {
	super(source,address, authorCred, seqno);
	this.rangeSet = _rangeSet;
	
    }



    /**
     * This method is called whenever the rm node receives a message for 
     * itself and wants to process it. The processing is delegated by rm 
     * to the message.
     * 
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
	    Id hash = new Id();
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
	msg = new RMResponseKeysMsg(rm.getLocalHandle(),rm.getAddress() , rm.getCredentials(), rm.m_seqno ++, returnedRangeSet);
	rm.route(null, msg, getSource());
    }

    
    
}





