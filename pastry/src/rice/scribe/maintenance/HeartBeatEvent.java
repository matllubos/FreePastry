package rice.scribe.maintenance;

import rice.scribe.*;
import rice.scribe.messaging.*;
import rice.pastry.*;
import rice.pastry.security.*;
import rice.pastry.routing.*;

import java.util.*;

public class HeartBeatEvent extends MaintenanceEvent
{
    public 
	HeartBeatEvent( Scribe scribe, Topic topic, ScribeScheduler sched ) {
	super( scribe, topic, sched );
    }

    public void run() {
	NodeId topicId = m_topic.getTopicId();
	Credentials cred = m_scribe.getCredentials();
	SendOptions opt = m_scribe.getSendOptions();
	
	ScribeMessage msgh = m_scribe.makeHeartBeatMessage( topicId, cred );
	
	// send message to all children in multicast subtree
	Iterator it = m_topic.getChildren().iterator();

	System.out.println( m_scribe.getNodeHandle() + "***************************************HEARTBEATEVENT**********************");
	
	while ( it.hasNext() ) {
	    NodeHandle nhandle = (NodeHandle)it.next();
	    System.out.println(m_scribe.getNodeHandle()+" HAD CHILDREN "+nhandle);
	    if( !m_scribe.routeMsgDirect( nhandle, msgh, cred, opt ) ) {
		
		/*if we are here, the child didnt respond so it is discarded*/
		m_topic.removeChild( nhandle );
		
		/*
		 * only if we arent subscribed, if we dont have children we 
		 * can forget about the topic
		 */
		if( !m_topic.isSubscribed() && !m_topic.hasChildren() ) {
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
