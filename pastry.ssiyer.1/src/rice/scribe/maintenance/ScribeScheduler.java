package rice.scribe.maintenance;

import rice.scribe.*;

import java.util.*;

/**
 * 
 */
class Scheduler 
{
    protected Timer m_timer;
    protected LinkedList m_list;
    protected int m_period;
    protected MaintenanceEvent m_current;

    Scheduler( int period ) {
	m_timer = new Timer(true);
	m_list = new LinkedList();
	m_period = period;
    }

    public void schedule( MaintenanceEvent ev ) {
	cancel( ev );
	m_list.addLast( ev );
	if( m_list.size() == 1 ) {
	    nextEvent();
	}
    }

    public void cancel( MaintenanceEvent ev ) {
	int index = m_list.indexOf( ev );

	if( index == 0 ) {
	    m_list.removeFirst();
	    m_timer.cancel();
	    m_timer = new Timer(true);

	    nextEvent();
	}
	else if( index > 0 ) {
	    m_list.remove( index );
	}
    }

    public void nextEvent() {
	if( m_list.size() == 0 )
	    return;

	MaintenanceEvent first = (MaintenanceEvent)m_list.getFirst();
	Date date = first.getDate();
	long timeToSchedule = date.getTime() + m_period;
	long timeAhead = timeToSchedule - (new Date()).getTime();

	if( timeAhead > 0 ) {
	    m_timer.schedule( first, timeAhead );
	}
	else {
	    m_timer.schedule( first, 0 );
	}
    }

    public void eventFinished( MaintenanceEvent ev ) {
	m_list.removeFirst();
	m_list.add( ev );
	nextEvent();
    }
}

/**
 * Scheduler that is used to invoke all the maintenance events in Scribe. It 
 * is represented as two separate threads that each maintain different types
 * of events, that occur with different frequencies.
 */
public class ScribeScheduler 
{
    private HeartBeatScheduler m_hbScheduler;
    private TreeRepairScheduler m_trScheduler;
    private Scribe m_scribe = null;

    public ScribeScheduler( Scribe scribe, int hbperiod, int trperiod ) {
	m_trScheduler = new TreeRepairScheduler( trperiod );
	m_hbScheduler = new HeartBeatScheduler( hbperiod );
	m_scribe = scribe;
    }

    /**
     * Schedules a heartbeat for the given topic.
     */
    public void scheduleHB( Topic topic ) {
	HeartBeatEvent ev = new HeartBeatEvent( m_scribe, topic, this );
	m_hbScheduler.schedule( ev );
    }

    /**
     * Schedules a tree repair for the given topic.
     */
    public void scheduleTR( Topic topic ) {
	TreeRepairEvent ev = new TreeRepairEvent( m_scribe, topic, this );
	m_trScheduler.schedule( ev );
    }

    /**
     * Cancels the heartbeat event related to the given topic.
     */
    public void cancelHB( Topic topic ) {
	HeartBeatEvent ev = new HeartBeatEvent( m_scribe, topic, this  );
	m_hbScheduler.cancel( ev );
    }

    /**
     * Cancels the tree repair event related to the given topic.
     */
    public void cancelTR( Topic topic ) {
	TreeRepairEvent ev = new TreeRepairEvent( m_scribe, topic, this  );
	m_trScheduler.cancel( ev );
    }

    /**
     * Invoked after the heartbeat is done and need to schedule the next event
     *
     * @param topic the topic where the event works. 
     */
    public void heartBeatFinished( Topic topic ) {
	HeartBeatEvent ev = new HeartBeatEvent( m_scribe, topic, this  );
	m_hbScheduler.eventFinished( ev );
    }

    /**
     * Invoked after the tree repair is done and need to schedule the next 
     * event
     *
     * @param topic the topic where the event works. 
     */
    public void treeRepairFinished( Topic topic ) {
	TreeRepairEvent ev = new TreeRepairEvent( m_scribe, topic, this  );
	m_trScheduler.eventFinished( ev );
    }
}

class TreeRepairScheduler extends Scheduler
{
    public TreeRepairScheduler( int period ) {
	super( period );
    }
}

class HeartBeatScheduler extends Scheduler
{
    public HeartBeatScheduler( int period ) {
	super( period );
    }
}
