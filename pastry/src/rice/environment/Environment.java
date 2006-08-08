/*
 * Created on Apr 6, 2005
 */
package rice.environment;

import java.io.IOException;
import java.util.*;

import rice.Destructable;
import rice.environment.logging.*;
import rice.environment.logging.file.FileLogManager;
import rice.environment.logging.simple.SimpleLogManager;
import rice.environment.params.Parameters;
import rice.environment.params.simple.SimpleParameters;
import rice.environment.processing.Processor;
import rice.environment.processing.sim.SimProcessor;
import rice.environment.processing.simple.SimpleProcessor;
import rice.environment.random.RandomSource;
import rice.environment.random.simple.SimpleRandomSource;
import rice.environment.time.TimeSource;
import rice.environment.time.simple.SimpleTimeSource;
import rice.environment.time.simulated.DirectTimeSource;
import rice.selector.SelectorManager;


/**
 * Used to provide properties, timesource, loggers etc to the FreePastry
 * apps and components.
 * 
 * XXX: Plan is to place the environment inside a PastryNode.
 * 
 * @author Jeff Hoye
 */
public class Environment implements Destructable {
  public static final String[] defaultParamFileArray = {"freepastry"};
   
  private SelectorManager selectorManager;
  private Processor processor;
  private RandomSource randomSource;
  private TimeSource time;
  private LogManager logManager;
  private Parameters params;
  private Logger logger;

  private HashSet destructables = new HashSet();
  
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
  public Environment(SelectorManager sm, Processor proc, RandomSource rs, TimeSource time, LogManager lm, Parameters params) {
    this.selectorManager = sm;    
    this.randomSource = rs;
    this.time = time; 
    this.logManager = lm;
    this.params = params;
    this.processor = proc;
    
    if (params == null) {
      throw new IllegalArgumentException("params cannot be null"); 
    }
    
    // choose defaults for all non-specified parameters
    chooseDefaults();
    
//    addDestructable(this.selectorManager);
//    addDestructable(this.processor);
    
    logger = this.logManager.getLogger(getClass(), null);
  }
  
  /**
   * Convienience for defaults.
   * 
   * @param paramFileName the file where parameters are saved
   * @throws IOException
   */
  public Environment(String[] orderedDefaultFiles, String paramFileName) {
    this(null,null,null,null,null,new SimpleParameters(orderedDefaultFiles,paramFileName));
  }
  
  public static Environment directEnvironment(int randomSeed) {
    SimpleRandomSource srs = new SimpleRandomSource(randomSeed, null);
    Environment env = directEnvironment(srs);
    srs.setLogManager(env.getLogManager());
    return env;
  }
  
  public static Environment directEnvironment() {
    return directEnvironment(null);
  }
  
  public static Environment directEnvironment(RandomSource rs) {
    Parameters params = new SimpleParameters(Environment.defaultParamFileArray,null);
    DirectTimeSource dts = new DirectTimeSource(params);
    LogManager lm = generateDefaultLogManager(dts,params);
    dts.setLogManager(lm);
    SelectorManager selector = generateDefaultSelectorManager(dts,lm);
    dts.setSelectorManager(selector);
    Processor proc = new SimProcessor(selector);
    Environment ret = new Environment(selector,proc,rs,dts,lm,
        params);
    return ret;
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
    if (time == null) {
      time = generateDefaultTimeSource(); 
    }
    if (logManager == null) {
      logManager = generateDefaultLogManager(time, params);
    }
    if (randomSource == null) {
      randomSource = generateDefaultRandomSource(params,logManager);
    }    
    if (selectorManager == null) {      
      selectorManager = generateDefaultSelectorManager(time, logManager); 
    }
    if (processor == null) {    
      if (params.contains("environment_use_sim_processor") &&
          params.getBoolean("environment_use_sim_processor")) {
        processor = new SimProcessor(selectorManager);
      } else {
        processor = generateDefaultProcessor(); 
      }
    }
  }
  
  public static RandomSource generateDefaultRandomSource(Parameters params, LogManager logging) {
    RandomSource randomSource;
    if (params.getString("random_seed").equalsIgnoreCase("clock")) {
      randomSource = new SimpleRandomSource(logging);
    } else {
      randomSource = new SimpleRandomSource(params.getLong("random_seed"), logging);      
    }
      
    return randomSource;
  }
  
  public static TimeSource generateDefaultTimeSource() {
    return new SimpleTimeSource();
  }
  
  public static LogManager generateDefaultLogManager(TimeSource time, Parameters params) {
    if (params.getBoolean("environment_logToFile")) {
      return new FileLogManager(time, params); 
    }
    return new SimpleLogManager(time, params); 
  }
  
  public static SelectorManager generateDefaultSelectorManager(TimeSource time, LogManager logging) {
    return new SelectorManager("Default", time, logging);
  }
  
  public static Processor generateDefaultProcessor() {
    return new SimpleProcessor("Default");
  }
  
  // Accessors
  public SelectorManager getSelectorManager() {
    return selectorManager; 
  }
  public Processor getProcessor() {
    return processor; 
  }
  public RandomSource getRandomSource() {
    return randomSource; 
  }
  public TimeSource getTimeSource() {
    return time; 
  }
  public LogManager getLogManager() {
    return logManager; 
  }
  public Parameters getParameters() {
    return params; 
  }
  
  /**
   * Tears down the environment.  Calls params.store(), selectorManager.destroy().
   *
   */
  public void destroy() {
    try {
      params.store();
    } catch (IOException ioe) {      
      if (logger.level <= Logger.WARNING) logger.logException("Error during shutdown",ioe); 
    }
    if (getSelectorManager().isSelectorThread()) {
      callDestroyOnDestructables();
    } else {
      getSelectorManager().invoke(new Runnable() {
        public void run() {
          callDestroyOnDestructables();
        }
      });
    }
  }
  
  private void callDestroyOnDestructables() {
    Iterator i = destructables.iterator();
    while(i.hasNext()) {
      Destructable d = (Destructable)i.next();
      d.destroy();
    }
    selectorManager.destroy();
    processor.destroy();    
  }

  public void addDestructable(Destructable destructable) {
    destructables.add(destructable);
    
  }
  
  public void removeDestructable(Destructable destructable) {
    destructables.remove(destructable);
  }
}

