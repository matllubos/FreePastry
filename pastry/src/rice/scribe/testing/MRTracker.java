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
 * @(#) MRTracker.java
 *
 * MessagesReceivedTracker. Keeps track of all messages received on all topics
 * in the current node.
 * @version $Id$
 * @author Romer Gil
 */
class MRTracker 
{

    HashMap m_topics;

    /**
     * Constructor
     */
    MRTracker() {
	m_topics = new HashMap();
    }

    /**
     * get topic ids (NodeId) for all 
     * subscribed topics
     * @return Array of TopicIds
     */
    NodeId[] getSubscribedTopics() {
	Set topicIds = m_topics.keySet();
	Iterator it = topicIds.iterator();
	while (it.hasNext()) { // remove unsubscribed topics
	    NodeId tid = (NodeId) it.next();
	    if (!isSubscribed(tid))
		it.remove();
	}
	NodeId[] tids = new NodeId[topicIds.size()];
	topicIds.toArray(tids);
	return tids;
    }

    /**
     * Returns the number of messages received for 
     * a given topic.
     * @param tid  The topicId 
     *
     * @return The number of messages received
     */
    int getMessagesReceived( NodeId tid ) {
	return ((CSPair)getPair( tid )).getCount();
    }

    void receivedMessage( NodeId tid ) {
	((CSPair)getPair( tid )).receivedMessage();
    }


    void setSubscribed( NodeId tid, boolean is ) {
	CSPair pair = (CSPair)m_topics.get( tid );
	if( pair == null ) {
	    pair = new CSPair();
	    m_topics.put( tid, pair );
	}

	((CSPair)getPair( tid )).setSubscribed(is);
    }


    boolean isSubscribed( NodeId tid ) {
	return ((CSPair)getPair( tid )).isSubscribed();
    }


    boolean knows( NodeId tid )
    {
	return (m_topics.get( tid ) != null);
    }


    private CSPair getPair( NodeId tid ) {
	CSPair pair = (CSPair)m_topics.get( tid );
	if( pair == null ) {
	    throw new Error( "Error in MRTracker: Pair for " 
				+ tid + "not found"  );
	}
	return pair;
    }


    void putTopic( NodeId tid ) {
	m_topics.put( tid, new CSPair() );
    }
}

