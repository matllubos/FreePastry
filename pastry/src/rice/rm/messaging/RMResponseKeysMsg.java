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


    private Vector reqRangeSet;

    // This range is the range of keys that the node requring to 
    // respond with the set of keys is responsible for
    private IdRange availRange;


    //private IdSet keySet;
    private Vector keySetSet;

    private boolean keySetStamp;

    //private Id stamp;
    private Vector stampSet;


    /**
     * Constructor : Builds a new RM Message
     */
    public RMResponseKeysMsg(NodeHandle source, Address address, Credentials authorCred, int seqno, Vector _reqRangeSet, IdRange _availRange, Vector _keySetSet, Vector _stampSet, boolean _keySetStamp ) {
	super(source,address, authorCred, seqno);
	this.reqRangeSet = _reqRangeSet;
	this.availRange = _availRange;
	this.keySetSet = _keySetSet;
	this.stampSet = _stampSet;
	this.keySetStamp = _keySetStamp;
    }



    /**
     * This method is called whenever the rm node receives a message for 
     * itself and wants to process it. The processing is delegated by rm 
     * to the message.
     * 
     */
    public void handleDeliverMessage( RMImpl rm) {
	//System.out.println(rm.getNodeId() + " received ResponseKeys msg from" + getSource().getNodeId());
	// We will see the keys sent, filter them on the basis of 
	// (myRange) and call fetch(IdSet) on the corresponding application

	IdSet fetchSet = new IdSet();
	IdSet keySet;
	if(!keySetStamp) {
	    for(int i=0; i< keySetSet.size(); i++) {
		keySet = (IdSet)keySetSet.elementAt(i);
		//System.out.println("keySet= " + keySet);
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
	    }
	    rm.app.fetch(fetchSet);
	}
	else {
	    // Should check if the stamps match. If not generate
	    // message to fetch keys with keySetStamp set to false
	    Id oHash, myHash;
	    Vector toAskFor = new Vector();
	    for(int i=0; i< stampSet.size(); i++) {
		oHash = (Id)stampSet.elementAt(i);
		//System.out.println("oHash= " + oHash);
		IdRange iRange = (IdRange) reqRangeSet.elementAt(i);
		IdSet myKeySet = rm.app.scan(iRange);
		myHash = myKeySet.getHash();
		if(!oHash.equals(myHash)) {
		    // We neeed to explicitly ask for keys in this range
		    toAskFor.add(iRange);
		}
	    }

	    if(toAskFor.size()!=0) {
		RMRequestKeysMsg msg;
		msg = new RMRequestKeysMsg(rm.getLocalHandle(),rm.getAddress() , rm.getCredentials(), rm.m_seqno ++, toAskFor, false);
		rm.route(null, msg, getSource());
		//System.out.println(rm.getNodeId() + "explicitly asking for keys");

	    }
	}
	
    }
    

}





