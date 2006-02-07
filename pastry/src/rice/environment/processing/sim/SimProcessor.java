/*
 * Created on Feb 6, 2006
 */
package rice.environment.processing.sim;

import rice.*;
import rice.environment.logging.LogManager;
import rice.environment.processing.*;
import rice.environment.processing.simple.ProcessingRequest;
import rice.environment.time.TimeSource;
import rice.selector.SelectorManager;

public class SimProcessor implements Processor {
  SelectorManager selector;
  
  public SimProcessor(SelectorManager selector) {
    this.selector = selector;
  }

  public void process(Executable task, Continuation command,
      SelectorManager selector, TimeSource ts, LogManager log) {
    selector.invoke(new ProcessingRequest(task, command, log, ts, selector));
  }

  public void processBlockingIO(WorkRequest request) {
    selector.invoke(request);
  }

  public void destroy() {
    // TODO Auto-generated method stub

  }

}
