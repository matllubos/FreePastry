/*
 * Created on Jun 28, 2005
 *
 */
package rice.environment.logging.file;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;

import rice.environment.Environment;
import rice.environment.logging.AbstractLogManager;
import rice.environment.logging.LogManager;
import rice.environment.logging.Logger;
import rice.environment.logging.simple.SimpleLogManager;
import rice.environment.logging.simple.SimpleLogger;
import rice.environment.params.Parameters;
import rice.environment.time.TimeSource;
import rice.p2p.commonapi.CancellableTask;
import rice.selector.SelectorManager;
import rice.selector.TimerTask;

/**
 * @author jstewart
 *
 */
public class RotatingLogManager extends AbstractLogManager {

  TimerTask rotateTask;
  
  public RotatingLogManager(TimeSource timeSource, Parameters params) {
    this(timeSource,params,"");
  }
  
  /**
   * @param timeSource
   * @param params
   * @param prefix
   */
  public RotatingLogManager(TimeSource timeSource, Parameters params, String prefix) {
    super(null, timeSource, params, prefix);
    rotate();
  }
  
  public void startRotateTask(SelectorManager sm) {
    if (rotateTask == null) {
      rotateTask = new LogRotationTask(); 
      sm.getTimer().schedule(rotateTask, params.getInt("log_rotate_interval"), params.getInt("log_rotate_interval"));
    } else {
      throw new RuntimeException("Task already started");
    }
  }
  
  public  void cancelRotateTask() {
    rotateTask.cancel();
    rotateTask = null;
  }

  void rotate() {
    synchronized (this) {
      PrintStream oldps = ps;
	    String filename = params.getString("log_rotate_filename");
	    String rot_filename = filename + "." + time.currentTimeMillis();
	    File oldfile = new File(filename);
	    if (oldfile.exists()) {
	      oldfile.renameTo(new File(rot_filename));
	    }
	    try {
	      ps = new PrintStream(new FileOutputStream(oldfile), true);
	      if (oldps != null)
	        oldps.close();
	    } catch (FileNotFoundException e) {
	      if (ps != oldps) { ps = oldps; }
	      System.err.println("could not rotate log "+filename+" because of "+e);
	      // XXX should also log it
	    }
    }
  }
  
  private class LogRotationTask extends TimerTask {
    public void run() {
      rotate();
    }
  }

  protected Logger constructLogger(String clazz, int level) {
    return new SimpleLogger(clazz,this,level);
  }
}
