package rice.scribe.maintenance;

import java.util.*;
import rice.scribe.*;


public abstract class MaintenanceEvent extends TimerTask
{
    protected Topic m_topic;
    protected Scribe m_scribe;
    protected ScribeScheduler m_scheduler;
    protected Date m_date;

    public 
	MaintenanceEvent( Scribe scribe, Topic topic, ScribeScheduler sched ) {
	m_topic = topic;
	m_scribe = scribe;
	m_scheduler = sched;
	m_date = new Date();
    }

    public boolean equals( Object o ) {
	MaintenanceEvent e = (MaintenanceEvent)o;
	return m_topic.getTopicId().equals( e.m_topic.getTopicId() );
    }

    public Date getDate() {
	return m_date;
    }
}
