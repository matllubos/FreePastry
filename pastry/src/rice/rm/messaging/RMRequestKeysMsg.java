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


    private boolean keySetStamp;

    private Vector rangeSet;


    /**
     * Constructor : Builds a new RM Message
     */
    public RMRequestKeysMsg(NodeHandle source, Address address, Credentials authorCred, int seqno, Vector _rangeSet, boolean _keySetStamp) {
	super(source,address, authorCred, seqno);
	this.keySetStamp = _keySetStamp;
	this.rangeSet = _rangeSet;
	
    }



    /**
     * This method is called whenever the rm node receives a message for 
     * itself and wants to process it. The processing is delegated by rm 
     * to the message.
     * 
     */
    public void handleDeliverMessage( RMImpl rm) {
	//System.out.println(rm.getNodeId() + "received RequestKeysMsg from " + getSource().getNodeId() + " with rangeSet.size = " + rangeSet.size());
	Vector keySetSet = new Vector();
	Vector stampSet = new Vector();

	Vector returnedRangeSet = new Vector();

	for(int i=0; i< rangeSet.size(); i++) {
	    IdRange reqRange;
	    IdRange iRange;
	    reqRange = (IdRange)rangeSet.elementAt(i);
	    //System.out.println("myRange= " + rm.myRange);
	    //System.out.println("reqRange= " + reqRange);
	    iRange = reqRange.intersect(rm.myRange);
	    //System.out.println("iRange= " + iRange);
	    if(iRange.isEmpty())
		continue;
	    IdSet keySet = rm.app.scan(iRange);
	    if(keySet.numElements()==0)
		continue;
	    returnedRangeSet.add(iRange);
	    if(keySetStamp) {
		stampSet.add(keySet.getHash());
	    }
	    else {
		keySetSet.add(keySet);
	    }
	}
	
	RMResponseKeysMsg msg;
	msg = new RMResponseKeysMsg(rm.getLocalHandle(),rm.getAddress() , rm.getCredentials(), rm.m_seqno ++, returnedRangeSet, rm.myRange, keySetSet, stampSet, keySetStamp );
	rm.route(null, msg, getSource());
    }

    
    
}





