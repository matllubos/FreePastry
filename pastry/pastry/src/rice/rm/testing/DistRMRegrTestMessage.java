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


package rice.rm.testing;

import rice.pastry.*;
import rice.pastry.security.*;
import rice.pastry.routing.*;
import rice.pastry.messaging.*;

import rice.rm.*;
import rice.rm.messaging.*;
import rice.rm.testing.*;

import java.io.*;
import java.util.*;

/**
 *
 * DistRMRegrTestMessage is used by the dist regression test
 * application on the RM layer to do system invariant checking. 
 * This message will be delivered to this application periodically 
 * so that the periodic testing activities can be accomplished.
 * 
 * @version $Id$ 
 * 
 * @author Animesh Nandi
 */


public class DistRMRegrTestMessage extends Message implements Serializable
{
    /**
     * Constructor
     *
     * @param addr the address of the DistRMRegrTestApp receiver.
     * @param c the credentials associated with the mesasge.
     */
    public 
	DistRMRegrTestMessage( Address addr, Credentials c) {
	super( addr, c );
    }
    
    /**
     * This method is called whenever the pastry node receives this message for the
     * DistRMRegrTestApp.
     * 
     * @param rmApp the DistRMRegrTestApp application.
     */
    public void handleDeliverMessage( DistRMRegrTestApp rmApp) {
	boolean result;
	int i;
	ObjectState state;
	NodeId objectKey;

	System.out.println("Periodic Invariant Check message: at " + rmApp.getNodeId());
	
	// Here we do the periodic increment of the values staleCount
	for(i=0; i< rmApp.m_numObjects; i ++) {
	    objectKey = (NodeId)rmApp.m_objectKeys.elementAt(i);
	    state = (ObjectState)rmApp.m_objects.get(objectKey);
	    if(state.isPresent())
		state.incrStaleCount();
	}



	// result checks for violations in system invariant over the last 
	// few time periods of this periodic message
	

	for(i=0; i< rmApp.m_numObjects; i ++) {
	    objectKey = (NodeId)rmApp.m_objectKeys.elementAt(i);
	    state = (ObjectState)rmApp.m_objects.get(objectKey);
	    if(state.isPresent()) {
		if(state.isStale())
		    System.out.println("ERROR: Object " + objectKey + " is stale at " + rmApp.getNodeId() + " for " + state.getstaleCount() + "periods");
	    }
	    else {
		if(state.isMissing())
		    System.out.println("ERROR: Object " + objectKey + "is missing at " + rmApp.getNodeId() + " for " + state.getmissingCount() + "periods");
	    }
	}

    }
    
    public String toString() {
	return new String( "DIST_RM_REGR_TEST  MSG:" );
    }
}









