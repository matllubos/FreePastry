package rice.scribe.testing;

import rice.pastry.*;
import rice.pastry.security.*;
import rice.scribe.*;
import rice.scribe.messaging.*;
import rice.pastry.direct.*;

import java.util.*;

/**
 * MessagesReceivedTracker. Keeps track of all messages received on all topics
 * in the current node.
 *
 * @author Romer Gil
 */
class MRTracker 
{
    HashMap m_topics;
    MRTracker() {
	m_topics = new HashMap();
    }
    // get topic ids (NodeId) for all 
    // subscribed topics
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

