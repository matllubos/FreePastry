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

package rice.scribe.testing;

import rice.pastry.*;
import rice.pastry.security.*;
import rice.scribe.*;
import rice.scribe.messaging.*;
import rice.pastry.direct.*;

import java.util.*;

/**
 * @(#) DirectScribeMaintenanceTestApp.java
 *
 * An application used by the DirectScribeMaintenanceTest suite for Scribe.
 *
 * @version $Id$
 *
 * @author Atul Singh
 * @author Animesh Nandi 
 */

public class DirectScribeMaintenanceTestApp implements IScribeApp
{

    private PastryNode m_pastryNode ;
    private Credentials m_credentials;
    public Scribe m_scribe;
    public int m_appCount;
  
    /**
     * Constructor
     * @param node The local PastryNode
     * @param scribe The underlying scribe 
     * @param cred The credentials 
     */
    public DirectScribeMaintenanceTestApp( PastryNode node, Scribe scribe, Credentials cred ) {
	m_scribe = scribe;
	m_credentials = cred;
	m_pastryNode = node;
	m_scribe.registerApp(this);
    }

    /**
     * Returns the underlying scribe object.
     * @return The underlying scribe object.
     */
    public Scribe getScribe() {
	return m_scribe;
    }


    public void scribeIsReady() {

    }

    /**
     * up-call invoked by scribe when a publish message is 'delivered'.
     */
    public void receiveMessage( ScribeMessage msg ) {
    }

    /**
     * up-call invoked by scribe when a publish message is forwarded through
     * the multicast tree.
     */
    public void forwardHandler( ScribeMessage msg ) {
	/*
	System.out.println("Node:" + getNodeId() + " App:"
                                + m_app + " forwarding: "+ msg);
	*/
    }
    
    /**
     * up-call invoked by scribe when a node detects a failure from its parent.
     */
    public void faultHandler( ScribeMessage msg, NodeHandle parent ) {
	/*
	System.out.println("Node:" + getNodeId() + " App:"
                                + m_app + " handling fault: " + msg);
	*/
    }

    /**
     * up-call invoked by scribe when a node is added to the multicast tree.
     */
    public void subscribeHandler( ScribeMessage msg ) {
	/*
	System.out.println("Node:" + getNodeId() + " App:"
                                + m_app + " child subscribed: " + msg);
	*/
    }

    public NodeId getNodeId() {
	return m_scribe.getNodeId();
    }

    /**
     * direct call to scribe for creating a topic from the current node.
     */
    public void create( NodeId topicId ) {
	m_scribe.create( topicId, m_credentials );
    }

    /**
     * direct call to scribe for publishing to a topic from the current node.
     */    
    public void multicast( NodeId topicId ) {
	m_scribe.multicast( topicId, null, m_credentials );
    }

    /**
     * direct call to scribe for subscribing to a topic from the current node.
     */    
    public void join( NodeId topicId ) {
	m_scribe.join( topicId, this, m_credentials );
    }

    /**
     * direct call to scribe for unsubscribing a  topic from the current node
     * The topic is chosen randomly if null is passed and topics exist.
     */    
    public void leave(NodeId topicId) {
	m_scribe.leave( topicId, this, m_credentials );
    }

}







