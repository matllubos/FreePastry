package rice.scribe.maintenance;

import rice.scribe.*;
import rice.scribe.messaging.*;
import rice.pastry.*;
import rice.pastry.security.*;
import rice.pastry.routing.*;

/**
 * MaintenanceEvent that is invoked by every node to detect when the parent
 * has failed so that the local node has to resubscribe.
 *
 * @author Romer Gil
 */
public class TreeRepairEvent extends MaintenanceEvent
{
    /**
     * Constructor
     *
     * @param scribe the Scribe system.
     * @param topic the topic on which this event operates.
     * @param the scheduler object that will reinvoke this event in the future.
     */
    public 
	TreeRepairEvent( Scribe scribe, Topic topic, ScribeScheduler sched ) {
	super( scribe, topic, sched );
    }

    /**
     * Method that is invoked by the java Timer object in the scheduler.
     */
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
