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
    private CSPair getPair( NodeId tid ) {
	CSPair pair = (CSPair)m_topics.get( tid );
	if( pair == null ) {
	    throw new Error( "Error in MRTracker" );
	}
	return pair;
    }
    void putTopic( NodeId tid ) {
	m_topics.put( tid, new CSPair() );
    }
}

