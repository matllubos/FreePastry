package rice.scribe.maintenance;

import rice.scribe.*;
import rice.scribe.messaging.*;
import rice.pastry.*;
import rice.pastry.security.*;
import rice.pastry.routing.*;

import java.util.*;

/**
 * MaintenanceEvent that is invoked by every node to send the heartbeat
 * messages to their children.
 *
 * @author Romer Gil
 * @author Eric Engineer
 */
public class HeartBeatEvent extends MaintenanceEvent
{
    /**
     * Constructor
     *
     * @param scribe the Scribe system.
     * @param topic the topic on which this event operates.
     * @param the scheduler object that will reinvoke this event in the future.
     */
    public 
	HeartBeatEvent( Scribe scribe, Topic topic, ScribeScheduler sched ) {
	super( scribe, topic, sched );
    }

    /**
     * Method that is invoked by the java Timer object in the scheduler.
     */
    public void run() {
	NodeId topicId = m_topic.getTopicId();
	Credentials cred = m_scribe.getCredentials();
	SendOptions opt = m_scribe.getSendOptions();
	
	ScribeMessage msgh = m_scribe.makeHeartBeatMessage( topicId, cred );
	
	// send message to all children in multicast subtree
	Iterator it = m_topic.getChildren().iterator();

	while ( it.hasNext() ) {
	    NodeHandle nhandle = (NodeHandle)it.next();

	    if( !m_scribe.routeMsgDirect( nhandle, msgh, cred, opt ) ) {
		
		/*if we are here, the child didnt respond so it is discarded*/
		m_topic.removeChild( nhandle );
		
		/*
		 * only if we have no apps subscribed, if we dont have children we 
		 * can forget about the topic
		 */
		if( !m_topic.hasSubscribers() && !m_topic.hasChildren() ) {
		    if ( m_topic.getParent() != null ) {
			ScribeMessage msgu = m_scribe.
			    makeUnsubscribeMessage( topicId, cred );
			
			m_scribe.routeMsg( topicId, msgu, cred, opt );
			
			// forget parent
			m_topic.setParent( null );
		    }
		    else { 
			// if parent unknown set waiting flag and wait until 
			// first event arrives
			m_topic.waitUnsubscribe( true );
		    }
		}
	    }
	}

	m_scheduler.heartBeatFinished( m_topic );
    }
}
