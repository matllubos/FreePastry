package org.mpisws.p2p.transport.peerreview.replay.playback;

import java.io.IOException;

import org.mpisws.p2p.transport.peerreview.Verifier;

import rice.environment.Environment;
import rice.environment.logging.LogManager;
import rice.environment.logging.Logger;
import rice.environment.time.TimeSource;
import rice.environment.time.simulated.DirectTimeSource;
import rice.selector.SelectorManager;
import rice.selector.TimerTask;

/**
 * There are normally 3 kinds of events:
 *   Invokations
 *   TimerTasks
 *   Network I/O
 *   
 * The Network I/O should match exactly with our Log, and so we only have to pump Invokations   
 *   
 * @author Jeff Hoye
 *
 */
public class ReplaySM extends SelectorManager {
  Verifier verifier;
  DirectTimeSource simTime;
  
  public ReplaySM(String instance, DirectTimeSource timeSource, LogManager log) {
    super(instance, timeSource, log);
    this.simTime = timeSource;
    setSelect(false);
  }
  
  public void setVerifier(Verifier v) {
    this.verifier = v;
  }

  /**
   * Don't automatically start the thread.
   */
  public void setEnvironment(Environment env) {
    if (env == null) throw new IllegalArgumentException("env is null!");
    if (environment != null) return;
    environment = env;
//    start();
  }

  @Override
  protected void executeDueTasks() {
    // Handle any pending timers. Note that we have to be sure to call them in the exact same
    // order as in the main code; otherwise there can be subtle bugs and side-effects. 
    if (logger.level <= Logger.FINER) logger.log("executeDueTasks()");

    boolean timerProgress = true;
    long now = verifier.getNextEventTime();
    while (timerProgress) {
      now = verifier.getNextEventTime();
//      timerProgress = false;
  
//     int best = -1;
//     for (int i=0; i<numTimers; i++) {
//       if ((timer[i].time <= now) && ((best<0) || (timer[i].time<timer[best].time) || ((timer[i].time==timer[best].time) && (timer[i].id<timer[best].id))))
//         best = i;
//     }
//  
//     if (best >= 0) {
//       int id = timer[best].id;
//       TimerCallback *callback = timer[best].callback;
//       now = timer[best].time;
//       timer[best] = timer[--numTimers];
//       vlog(2, "Verifier: Timer expired (#%d, now=%lld)", id, now);
//       callback->timerExpired(id);
//       timerProgress = true;
//     }
//   }
//
//    
      TimerTask next = null;
      
      synchronized (this) {
        if (timerQueue.size() > 0) {
          next = (TimerTask) timerQueue.peek();
          if (next.scheduledExecutionTime() <= now) {
            timerQueue.poll(); // remove the event
            simTime.setTime(next.scheduledExecutionTime()); // set the time            
          } else {
            timerProgress = false;
          }
        } else {
          timerProgress = false;
        }
      }
      
      if (timerProgress) {
        super.doInvocations();
        if (logger.level <= Logger.FINE) logger.log("executing task "+next);
        if (next.execute(simTime)) { // execute the event
          synchronized(this) {
            timerQueue.add(next); // if the event needs to be rescheduled, add it back on
          }
        }
      }
    }
    simTime.setTime(now); // so we always make some progress
    super.doInvocations();
    
    try {
      verifier.makeProgress();
      if (!verifier.verifiedOK()) throw new RuntimeException("Verification failed.");
    } catch (IOException ioe) {
      throw new RuntimeException(ioe);
    }
  }  
  
  @Override
  protected void doInvocations() {
    // do nothing, this is called in executeDueTasks
  }
}
