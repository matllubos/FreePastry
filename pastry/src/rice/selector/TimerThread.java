/*
 * Created on Jul 26, 2004
 */
package rice.selector;

import java.io.IOException;
import java.nio.channels.Selector;

/**
 * @author Jeff Hoye
 */
/**
 * This "helper class" implements the timer's task execution thread, which
 * waits for tasks on the timer queue, executions them when they fire,
 * reschedules repeating tasks, and removes cancelled tasks and spent
 * non-repeating tasks from the queue.
 */
public class TimerThread /*extends Thread*/ {
    /**
     * This flag is set to false by the reaper to inform us that there
     * are no more live references to our Timer object.  Once this flag
     * is true and there are no more tasks in our queue, there is no
     * work left for us to do, so we terminate gracefully.  Note that
     * this field is protected by queue's monitor!
     */
    boolean newTasksMayBeScheduled = true;

    /**
     * Our Timer's queue.  We store this reference in preference to
     * a reference to the Timer so the reference graph remains acyclic.
     * Otherwise, the Timer would never be garbage-collected and this
     * thread would never go away.
     */
    protected TaskQueue queue;
    private Selector selector;

    public TimerThread(TaskQueue queue, Selector selector) {
        this.queue = queue;
        this.selector = selector;
    }

//    public void run() {
//        try {
//            mainLoop();
//        } finally {
//            // Somone killed this Thread, behave as if Timer cancelled
//            synchronized(queue) {
//                newTasksMayBeScheduled = false;
//                queue.clear();  // Eliminate obsolete references
//            }
//        }
//    }

    /**
     * The main timer loop.  (See class comment.)
     */
    private void mainLoop() {
        while (true) {
//            try {
//              mainLoopHelper(null);
//            } catch(InterruptedException e) {
//            }
        }
    }

    void mainLoopHelper(SelectorManager s) throws IOException {
      TimerTask task;
      boolean taskFired;
      
//      synchronized(queue) {
          // Wait for queue to become non-empty
          if (queue.isEmpty() && newTasksMayBeScheduled)
//              queue.wait();
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
      if (taskFired)  // Task fired; run it, holding no locks
          task.run();
    }
    
}