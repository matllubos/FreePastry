/*******************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate

Copyright 2002-2007, Rice University. Copyright 2006-2007, Max Planck Institute 
for Software Systems.  All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither the name of Rice  University (RICE), Max Planck Institute for Software 
Systems (MPI-SWS) nor the names of its contributors may be used to endorse or 
promote products derived from this software without specific prior written 
permission.

This software is provided by RICE, MPI-SWS and the contributors on an "as is" 
basis, without any representations or warranties of any kind, express or implied 
including, but not limited to, representations or warranties of 
non-infringement, merchantability or fitness for a particular purpose. In no 
event shall RICE, MPI-SWS or contributors be liable for any direct, indirect, 
incidental, special, exemplary, or consequential damages (including, but not 
limited to, procurement of substitute goods or services; loss of use, data, or 
profits; or business interruption) however caused and on any theory of 
liability, whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even if 
advised of the possibility of such damage.

*******************************************************************************/ 
/*
 * Created on Nov 8, 2005
 */
package rice.environment.time.simulated;

import rice.environment.logging.*;
import rice.environment.params.Parameters;
import rice.environment.time.TimeSource;
import rice.selector.*;

public class DirectTimeSource implements TimeSource {

  private long time = 0;
  private Logger logger = null;
  private String instance;
  private SelectorManager selectorManager;
  
  public DirectTimeSource(long time) {
    this(time, null);
  }

  public DirectTimeSource(long time, String instance) {
    if (time < 0) {
      time = System.currentTimeMillis();
    } else {
      this.time = time; 
    }
    this.instance = instance;
  }
  
  public DirectTimeSource(Parameters p) {
    this(p.getLong("direct_simulator_start_time")); 
  }

  public void setLogManager(LogManager manager) {
    logger = manager.getLogger(DirectTimeSource.class, instance);
  }
  
  public void setSelectorManager(SelectorManager sm) {
    selectorManager = sm;
  }
  
  public long currentTimeMillis() {
    return time;
  }
  
  /**
   * Should be synchronized on the selectorManager
   * @param newTime
   */
  public void setTime(long newTime) {
    if (newTime < time) {
      throw new RuntimeException("Attempted to set time from "+time+" to "+newTime+".");
    }
    if (logger.level <= Logger.FINER) logger.log("DirectTimeSource.setTime("+time+"=>"+newTime+")");
    time = newTime;
  }

  /**
   * Should be synchronized on the selectorManager
   */
  public void incrementTime(int millis) {
    setTime(time+millis); 
  }

  private class BlockingTimerTask extends TimerTask {
    boolean done = false;
    
    public void run() {
      synchronized(selectorManager) {
        done = true;
        selectorManager.notifyAll();
        // selector already yields enough
//        Thread.yield();
      }
    }
    
  }
  
  public void sleep(long delay) throws InterruptedException {
    synchronized(selectorManager) { // to prevent an out of order acquisition
      // we only lock on the selector
      BlockingTimerTask btt = new BlockingTimerTask();
      if (logger.level <= Logger.FINE) logger.log("DirectTimeSource.sleep("+delay+")");
      
      selectorManager.getTimer().schedule(btt,delay);
      
      while(!btt.done) {
        selectorManager.wait(); 
      }
    }
  }
  
}
