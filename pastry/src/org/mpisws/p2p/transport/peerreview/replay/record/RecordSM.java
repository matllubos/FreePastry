package org.mpisws.p2p.transport.peerreview.replay.record;

import rice.environment.logging.LogManager;
import rice.environment.time.TimeSource;
import rice.environment.time.simulated.DirectTimeSource;
import rice.selector.SelectorManager;
import rice.selector.TimerTask;

/**
 * This is the SelectorManager for PeerReview.  The invariant here is that we use a simTime that isn't updated near as 
 * frequently as the real clock.  This makes the events more discrete for replay.
 * 
 * @author Jeff Hoye
 *
 */
public class RecordSM extends SelectorManager {
  DirectTimeSource simTime;
  TimeSource realTime;
  
  public RecordSM(String instance, TimeSource realTime, DirectTimeSource simTime, LogManager log) {
    super(instance, simTime, log);
    this.realTime = realTime;
    this.simTime = simTime;
  }

  @Override
  protected void executeDueTasks() {
    //System.out.println("SM.executeDueTasks()");
    long now = realTime.currentTimeMillis();
        
    synchronized (this) {
      boolean done = false;
      while (!done) {
        if (timerQueue.size() > 0) {
          TimerTask next = (TimerTask) timerQueue.peek();
          if (next.scheduledExecutionTime() <= now) {
            timerQueue.poll(); // remove the event
            simTime.setTime(next.scheduledExecutionTime()); // set the time
            if (next.execute(simTime)) { // execute the event
              timerQueue.add(next); // if the event needs to be rescheduled, add it back on
            }
          } else {
            done = true;
          }
        } else {
          done = true;
        }
      }
    }
    simTime.setTime(now); // so we always make some progress
  }
}
