/*
 * Created on Aug 9, 2005
 */
package rice.environment.processing;

import rice.*;
import rice.environment.logging.LogManager;
import rice.environment.time.TimeSource;
import rice.selector.SelectorManager;

/**
 * Provides a mechanism to do time consuming tasks off of FreePastry's selecto thread.
 * 
 * Usually acquired by calling environment.getProcessor().
 * 
 * @author Jeff Hoye
 */
public interface Processor extends Destructable {
  /**
   * Schedules a job for processing on the dedicated processing thread.  CPU intensive jobs, such
   * as encryption, erasure encoding, or bloom filter creation should never be done in the context
   * of the underlying node's thread, and should only be done via this method.  The continuation will
   * be called on the Selector thread.
   *
   * @param task The task to run on the processing thread
   * @param command The command to return the result to once it's done
   */
  public void process(Executable task, Continuation command, SelectorManager selector, TimeSource ts, LogManager log);

  /**
   * Schedules a different type of task.  This thread is for doing Disk IO that is required to be blocking.
   * 
   * @param request
   */
  public void processBlockingIO(WorkRequest request);
  
  /**
   * Shuts down the processing thread.
   */
  public void destroy();
}
