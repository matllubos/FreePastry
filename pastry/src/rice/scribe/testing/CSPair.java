package rice.scribe.testing;

import rice.pastry.*;
import rice.pastry.security.*;
import rice.scribe.*;
import rice.scribe.messaging.*;
import rice.pastry.direct.*;

import java.util.*;

/**
 * CountSubscribedPair keeps track for a single topic of how many messages have
 * been received from it and whether we are subscribed to it or not. 
 */
class CSPair {
    private int m_count;
    private boolean m_isSubscribed;
    CSPair() {
	m_count = 0;
	m_isSubscribed = false;
    }
    int getCount() { return m_count; }
    void receivedMessage() { m_count++; }
    boolean isSubscribed() { return m_isSubscribed; }
    void setSubscribed( boolean sub ) { m_isSubscribed = sub; }
}
