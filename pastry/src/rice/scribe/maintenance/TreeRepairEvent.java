package rice.scribe.maintenance;

import rice.scribe.*;
import rice.scribe.messaging.*;
import rice.pastry.*;
import rice.pastry.security.*;
import rice.pastry.routing.*;

public class TreeRepairEvent extends MaintenanceEvent
{
    public 
	TreeRepairEvent( Scribe scribe, Topic topic, ScribeScheduler sched ) {
	super( scribe, topic, sched );
    }

    public void run() {
	NodeId topicId = m_topic.getTopicId();
	NodeId myNodeId = m_scribe.getNodeId();
	Credentials cred = m_scribe.getCredentials();
	SendOptions opt = m_scribe.getSendOptions();
	
	ScribeMessage msg = m_scribe.makeSubscribeMessage( topicId, cred );

	IScribeApp app = m_scribe.getScribeApp();
	app.faultHandler( msg );

	m_scribe.routeMsg( topicId, msg, cred, opt );
	
	m_scheduler.treeRepairFinished( m_topic );
    }
}
