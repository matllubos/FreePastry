/*
 * Created on Aug 16, 2005
 */
package rice.environment.processing.simple;

import rice.environment.processing.WorkRequest;

/**
 * @author Jeff Hoye
 */
public class BlockingIOThread extends Thread {
  WorkQueue workQ;

  boolean running = true;

  public BlockingIOThread(WorkQueue workQ) {
    super("Persistence Worker Thread");
    this.workQ = workQ;
  }

  public void run() {
    running = true;
    while (running) {
      WorkRequest wr = workQ.dequeue();
      if (wr != null)
        wr.run();
    }
  }

  public void destroy() {
    running = false;
  }
}