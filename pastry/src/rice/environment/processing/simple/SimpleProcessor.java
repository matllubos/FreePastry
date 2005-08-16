/*
 * Created on Aug 9, 2005
 */
package rice.environment.processing.simple;

import rice.*;
import rice.environment.logging.LogManager;
import rice.environment.processing.*;
import rice.environment.processing.Processor;
import rice.environment.time.TimeSource;
import rice.selector.SelectorManager;

/**
 * @author Jeff Hoye
 */
public class SimpleProcessor implements Processor {
  // the queue used for processing requests
  private ProcessingQueue QUEUE;
  private ProcessingThread THREAD;

  // for blocking IO WorkRequests
  private WorkQueue workQueue;
  private BlockingIOThread bioThread;
  
  public SimpleProcessor(String name) {
    QUEUE = new ProcessingQueue();
    THREAD = new ProcessingThread(name+".ProcessingThread",QUEUE);
    THREAD.start();
    THREAD.setPriority(Thread.MIN_PRIORITY);    
    workQueue = new WorkQueue();
    bioThread = new BlockingIOThread(workQueue);
    bioThread.start();
  }
  
  /**
   * Schedules a job for processing on the dedicated processing thread.  CPU intensive jobs, such
   * as encryption, erasure encoding, or bloom filter creation should never be done in the context
   * of the underlying node's thread, and should only be done via this method.  
   *
   * @param task The task to run on the processing thread
   * @param command The command to return the result to once it's done
   */
  public void process(Executable task, Continuation command, SelectorManager selector, TimeSource ts, LogManager log) {
    QUEUE.enqueue(new ProcessingRequest(task, command, log, ts, selector));
  }

  public void processBlockingIO(WorkRequest workRequest) {
    workQueue.enqueue(workRequest);
  }
  
  
  public ProcessingQueue getQueue() {
    return QUEUE;
  }
  
  public void destroy() {
    THREAD.destroy();
    QUEUE.destroy();
    bioThread.destroy();
    workQueue.destroy();
  }
  
  public WorkQueue getIOQueue() {
    return workQueue;
  }
}
