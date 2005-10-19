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

import java.io.*;
import java.util.*;

/**
 *
 * DistRMRegrTestReplicateMessage is used by the dist regression test
 * application on the RM layer to do the initial replication of a few
 * objects into the system and the periodic refreshing.
 * Note that this will be done only by the first node that forms
 * the pastry ring. 
 * 
 * @version $Id$ 
 * 
 * @author Animesh Nandi
 */


public class DistRMRegrTestReplicateMessage extends Message implements Serializable
{
    /**
     * Constructor
     *
     * @param addr the address of the DistRMRegrTestApp receiver.
     * @param c the credentials associated with the mesasge.
     */
    public 
	DistRMRegrTestReplicateMessage( Address addr, Credentials c) {
	super( addr, c );
    }
    
    /**
     * This method is called whenever the pastry node receives this message for the
     * DistRMRegrTestApp.
     * 
     * @param rmApp the DistRMRegrTestApp application.
     */
    public void handleDeliverMessage( DistRMRegrTestApp rmApp) {
	int i;
	NodeId objectKey;

	if(!rmApp.replicationDone) {

	    System.out.println("Replication message: at " + rmApp.getNodeId());

	    //for(i=0; i< rmApp.m_numObjects; i ++) {
	    i = rmApp.numReplicated;
	    System.out.println("Replicating object= " + i);
	    objectKey = (NodeId)rmApp.m_objectKeys.elementAt(i);
	    rmApp.replicate(objectKey, rmApp.m_replicaFactor);

	    //}
	    rmApp.numReplicated ++;
	    if(rmApp.numReplicated == rmApp.m_numObjects)
		rmApp.replicationDone = true;

	}
	else {

	    System.out.println("Periodic Refresh message: at " + rmApp.getNodeId());

	    //for(i=0; i< rmApp.m_numObjects; i ++) {
	    i = rmApp.numRefreshed;
	    System.out.println("Refreshing object= " + i);
	    objectKey = (NodeId)rmApp.m_objectKeys.elementAt(i);
	    rmApp.heartbeat(objectKey, rmApp.m_replicaFactor);

	    //}
	    rmApp.numRefreshed = (rmApp.numRefreshed + 1)% rmApp.m_numObjects;
	}
	    
    }
    
    public String toString() {
	return new String( "DIST_RM_REGR_TEST_REPLICATE  MSG:" );
    }
}









