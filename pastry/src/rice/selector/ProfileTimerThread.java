/*
 * Created on Aug 24, 2004
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package rice.selector;

import java.io.IOException;
import java.nio.channels.Selector;

/**
 * @author jeffh
 *
 * To change the template for this generated type comment go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
public class ProfileTimerThread extends TimerThread {

	/**
	 * @param queue
	 * @param selector
	 */
	public ProfileTimerThread(TaskQueue queue, Selector selector) {
		super(queue, selector);
	}

  void mainLoopHelper(SelectorManager s) throws IOException {
    TimerTask task;
    boolean taskFired;
      
//    synchronized(queue) {
        // Wait for queue to become non-empty
        if (queue.isEmpty() && newTasksMayBeScheduled)
//            queue.wait();
            s.select(0);
        if (queue.isEmpty())
            return; // Queue is empty and will forever remain; die

        // Queue nonempty; look at first evt and do the right thing
        long currentTime, executionTime;
     synchronized (queue) {
        task = queue.getMin();
        synchronized(task.lock) {
            if (task.state == TimerTask.CANCELLED) {
                queue.removeMin();
                return;  // No action required, poll queue again
            }
            currentTime = System.currentTimeMillis();
            executionTime = task.nextExecutionTime;
            if (taskFired = (executionTime<=currentTime)) {
                if (task.period == 0) { // Non-repeating, remove
                    queue.removeMin();
                    task.state = TimerTask.EXECUTED;
                } else { // Repeating task, reschedule
                    queue.rescheduleMin(
                      task.period<0 ? currentTime   - task.period
                                    : executionTime + task.period);
                }
            }
        }
      }
       
      if (!taskFired) // Task hasn't yet fired; wait
        s.select((int)(executionTime - currentTime));
            //queue.wait(executionTime - currentTime);
   //} // synchronized
    if (taskFired) { // Task fired; run it, holding no locks
      ((ProfileSelector)s).lastTaskType = "timer";
      ((ProfileSelector)s).lastTaskClass = task.getClass().getName();
      ((ProfileSelector)s).lastTaskToString = task.toString();
      ((ProfileSelector)s).lastTaskHash = System.identityHashCode(task);
      task.run();
      ((ProfileSelector)s).lastTaskType = "timer complete";
    }
  }


}
