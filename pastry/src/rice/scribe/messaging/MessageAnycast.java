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

package rice.scribe.messaging;

import rice.pastry.*;
import rice.pastry.security.*;
import rice.pastry.routing.*;
import rice.pastry.messaging.*;
import rice.scribe.*;
import rice.scribe.security.*;
import rice.scribe.maintenance.*;

import java.io.*;
import java.util.*;

/**
 *
 * MessageAnycast is used whenever a Scribe node wishes to anycast 
 * to a particular topic. 
 * 
 * @version $Id$ 
 *
 * @author Animesh Nandi
 * @author Atul Singh
 */


public class MessageAnycast extends ScribeMessage implements Serializable
{
    /**
     * Holds the list of NodeHandles to send to (used for DFS)
     */
    Vector send_to;

    /**
     * Holds the list of NodeHandles already examined (used for DFS)
     */
    Vector already_seen;

    /**
     * Credentials
     */
    Credentials c;

    /**
     * Constructor
     *
     * @param addr the address of the scribe receiver.
     * @param source the node generating the message.
     * @param topicId the topic to which this message refers to.
     * @param cred the credentials associated with the mesasge.
     */
    public 
	MessageAnycast( Address addr, NodeHandle source, 
			NodeId topicId, Credentials cred ) {
	super( addr, source, topicId, cred );
	send_to = new Vector();
	already_seen = new Vector();
	c = cred;
    }
    
    /**
     * This method is called whenever the scribe node receives a message for 
     * itself and wants to process it. The processing is delegated by scribe 
     * to the message.
     * 
     * @param scribe the scribe application.
     * @param topic the topic within the scribe application.
     */
    public void 
	handleDeliverMessage( Scribe scribe,Topic topic ) {

	handleForwardMessage(scribe, topic);
	return;
    }
    

    /**
     * This method is called whenever the scribe node forwards a message in 
     * the scribe network. The processing is delegated by scribe to the 
     * message.
     * 
     * @param scribe the scribe application.
     * @param topic the topic within the scribe application.
     *
     * @return true if the message should be routed further, false otherwise.
     */
    public boolean 
	handleForwardMessage(Scribe scribe, Topic topic ) {
	//System.out.println("MessageAnycast -- Forwarded at "+scribe.getNodeId()+" source "+m_source.getNodeId());

	
	// first check if local node has already been visited,
	// if yes, then check if we know who to send this message
	// next to otherwise keep forwording through pastry
	if(already_seen.contains(scribe.getLocalHandle())){
	    if(send_to.size() > 0){
		send_to.remove(0);
	    }
	    if(send_to.size() > 0){
		scribe.routeMsgDirect( (NodeHandle)send_to.get(0), this, c, null );
		return false;
	    }
	    else
		return true;
	}
	// Node is not part of the tree, and don't have a list 
	// built up of anywhere else to forward this message to 
	if( ( topic == null) && ( send_to.size() == 0 ) ){
	    return true;
	}
        // Receiving node is not part of the tree
	if( topic == null){
	    send_to.remove(0);

            // Something in the send_to list, so we can forward the message along
	    if ( send_to.size() > 0 ){
		scribe.routeMsgDirect( (NodeHandle)send_to.get(0), this, c, null );
	    }
            // Something broke along the way, and we can't find our way back to the
	    // spare capacity tree 
	    else{
		System.out.println("DFS FAILED :: No spare capacity");
		// Call the fault handler, since no one could satisfy 
		// the anycast request.
		faultHandler(scribe);
	    }
	}
	else {
	    // Local node is part of the tree, so should implement
	    // the DFS.
	    Vector children = scribe.getChildren(topic.getTopicId());
            Vector toAdd = new Vector();
            NodeHandle child;
	    boolean result = true;
            
            // Check if all my children are already visited or not,
            // if visited, then I will check if I can take this child
	    // and if not, return to parent.
            for(int i = 0; i < children.size(); i++){
                child = (NodeHandle)children.elementAt(i);
                if(!already_seen.contains(child) && !send_to.contains(child))
                    toAdd.add(child);
            }
            // My children have not been visited, so adding the children and then
	    // adding the local node itself, so that DFS search should come back
	    // to me if sub-tree under me is unable to satisfy the request
            if(toAdd.size() > 0){
                if(!send_to.contains(scribe.getLocalHandle()))
		  send_to.add( 0, scribe.getLocalHandle());
                send_to.addAll(0, toAdd);
		scribe.routeMsgDirect( (NodeHandle)send_to.get(0), this, c, null );
		return false;
	    }
	    else {
		// If local node is a leaf, then check if local registered
		// applications can satisfy the anycast request.
		IScribeApp[] apps = topic.getApps();
		
		for (int i=0; i<apps.length && result; i++) {
		    result = apps[i].anycastHandler(this);
		}
	    }
	    
	    // if result == false, then that means one of registered
	    // application was able to satisfy the request, and hence
	    // anycast message should not be routed furthur for DFS.
	    if(result == false)
		return false;
	    else {
		if(!already_seen.contains(scribe.getLocalHandle()))
		   already_seen.add(scribe.getLocalHandle());
		if(send_to.contains(scribe.getLocalHandle())){
		    //System.out.println("Send to contains local node -- FINE");
		    send_to.remove((Object)scribe.getLocalHandle());
		}
		if(send_to.size() > 0)
		    scribe.routeMsgDirect( (NodeHandle)send_to.get(0), this, c, null );
		else {
		    //Sending to parent if not root
		    if ( !scribe.isRoot( topic.getTopicId() ) ){
			if(scribe.getParent( topic.getTopicId()) != null){
			    scribe.routeMsgDirect( scribe.getParent( topic.getTopicId() ), this, c, null );
			}
			else {
			    System.out.println("WARNING -- Parent is null for non-root node.. should handle this case -- At "+scribe.getNodeId()+" for topic "+topic.getTopicId()+" already_seen_size "+alreadySeenSize());
			    // We are not part of tree anymore, so should 
			    // anycast furthur this message
			    scribe.anycast(topic.getTopicId(), this, c);
			}
		    }
		    //This node is the root, which means DFS failed
		    else {						
			// call the fault handler to notify that DFS
			// has failed.
			System.out.println("DFS Failed at"+scribe.getNodeId()+" for topic "+m_topicId);
			this.faultHandler(scribe);
		    }						
		}
	    }
	}
	return false;
    }
    
    /**
     * Method returns the number of nodes visited during
     * the DFS.
     */
    public int alreadySeenSize(){
	return already_seen.size();
    }
    /**
     * Method to notify that anycast has failed.
     * Derived classes can do more application-specific handling
     * of such cases.
     */
    public void faultHandler(Scribe scribe){
	System.out.println("ANYCAST FAILED !!"+toString());
    }

    public String toString() {
	return new String( "ANYCAST MSG:" + m_source.getNodeId()+ " topicId "+getTopicId() );
    }
}
