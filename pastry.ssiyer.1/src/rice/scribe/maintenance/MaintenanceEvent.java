package rice.scribe.maintenance;

import java.util.*;
import rice.scribe.*;


/**
 * Abstract implementation of a MaintenanceEvent.
 *
 * @author Romer Gil
 */
public abstract class MaintenanceEvent extends TimerTask
{
    protected Topic m_topic;
    protected Scribe m_scribe;
    protected ScribeScheduler m_scheduler;
    protected Date m_date;

    /**
     * Constructor
     *
     * @param scribe the Scribe system.
     * @param topic the topic on which this event operates.
     * @param the scheduler object that will reinvoke this event in the future.
     */
    public 
	MaintenanceEvent( Scribe scribe, Topic topic, ScribeScheduler sched ) {
	m_topic = topic;
	m_scribe = scribe;
	m_scheduler = sched;
	m_date = new Date();
    }

    /**
     * From Comparable.
     */
    public boolean equals( Object o ) {
	MaintenanceEvent e = (MaintenanceEvent)o;
	return m_topic.getTopicId().equals( e.m_topic.getTopicId() );
    }

    /**
     * Returns the date object in the event.
     *
     * @return the date when the object was created.
     */
    public Date getDate() {
	return m_date;
    }
}
