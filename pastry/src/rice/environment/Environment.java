/*
 * Created on Apr 6, 2005
 */
package rice.environment;

import rice.environment.logging.*;
import rice.environment.logging.simple.SimpleLogManager;
import rice.environment.params.Parameters;
import rice.environment.params.simple.SimpleParameters;
import rice.environment.random.RandomSource;
import rice.environment.random.simple.SimpleRandomSource;
import rice.environment.time.TimeSource;
import rice.environment.time.simple.SimpleTimeSource;
import rice.selector.SelectorManager;


/**
 * Used to provide properties, timesource, loggers etc to the FreePastry
 * apps and components.
 * 
 * XXX: Plan is to place the environment inside a PastryNode.
 * 
 * @author Jeff Hoye
 */
public class Environment {

  public SelectorManager selectorManager;
  public RandomSource randomSource;
  public TimeSource time;
  public LogManager logging;
  public Parameters params;
  
  /**
   * Constructor.  You can provide null values for all/any paramenters, which will result
   * in a default choice.  If you want different defaults, consider extending Environment
   * and providing your own chooseDefaults() method.
   * 
   * @param sm the SelectorManager.  Default: rice.selector.SelectorManager
   * @param rs the RandomSource.  Default: rice.environment.random.simple.SimpleRandomSource
   * @param time the TimeSource.  Default: rice.environment.time.simple.SimpleTimeSource
   * @param lm the LogManager.  Default: rice.environment.logging.simple.SimpleLogManager
   * @param props the Properties.  Default: empty properties
   */
  public Environment(SelectorManager sm, RandomSource rs, TimeSource time, LogManager lm, Parameters params) {
    this.selectorManager = sm;
    this.randomSource = rs;
    this.time = time; 
    this.logging = lm;
    this.params = params;
    
    // choose defaults for all non-specified parameters
    chooseDefaults();
  }
  
  /**
   * Can be easily overridden by a subclass.
   */
  protected void chooseDefaults() {
    // choose defaults for all non-specified parameters
    if (selectorManager == null) {
      selectorManager = new SelectorManager(false); 
    }
    if (randomSource == null) {
      randomSource = new SimpleRandomSource(); 
    }
    if (time == null) {
      time = new SimpleTimeSource(); 
    }
    if (logging == null) {
      logging = new SimpleLogManager(time, Logger.SEVERE); 
    }
    if (params == null) {
      params = new SimpleParameters(); 
    }    
  }
  
  // Accessors
  public SelectorManager getSelectorManager() {
    return selectorManager; 
  }
  public RandomSource getRandomSource() {
    return randomSource; 
  }
  public TimeSource getTimeSource() {
    return time; 
  }
  public LogManager getLogManager() {
    return logging; 
  }
  public Parameters getParameters() {
    return params; 
  }
}

