/*
 * Created on Apr 6, 2005
 */
package rice.environment;

import java.io.IOException;

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
  public static final String[] defaultParamFileArray = {"freepastry"};
   
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
    
    if (params == null) {
      throw new IllegalArgumentException("params cannot be null"); 
    }
    
    // choose defaults for all non-specified parameters
    chooseDefaults();
  }
  
  /**
   * Convienience for defaults.
   * 
   * @param paramFileName the file where parameters are saved
   * @throws IOException
   */
  public Environment(String[] orderedDefaultFiles, String paramFileName) {
    this(null,null,null,null,new SimpleParameters(orderedDefaultFiles,paramFileName));
  }
  
  public Environment(String paramFileName) {
    this(defaultParamFileArray,paramFileName);
  }

  /**
   * Convienience for defaults.  Has no parameter file to load/store.
   */
  public Environment() {
    this(null);
  }

  /**
   * Can be easily overridden by a subclass.
   */
  protected void chooseDefaults() {
    // choose defaults for all non-specified parameters
//    if (params == null) {      
//      params = new SimpleParameters("temp"); 
//    }    
    if (randomSource == null) {
      randomSource = new SimpleRandomSource(params.getInt("random_seed")); 
    }    
    if (time == null) {
      time = new SimpleTimeSource(); 
    }
    if (logging == null) {
      logging = new SimpleLogManager(time, params); 
    }
    if (selectorManager == null) {      
      selectorManager = new SelectorManager(false, "Default", time, logging); 
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

