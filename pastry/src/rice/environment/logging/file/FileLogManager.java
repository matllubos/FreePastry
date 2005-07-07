/*
 * Created on Jul 7, 2005
 */
package rice.environment.logging.file;

import java.io.*;
import java.io.PrintStream;

import rice.environment.logging.LogManager;
import rice.environment.logging.simple.SimpleLogManager;
import rice.environment.params.Parameters;
import rice.environment.time.TimeSource;
import rice.environment.time.simple.SimpleTimeSource;

/**
 * @author Jeff Hoye
 */
public class FileLogManager extends SimpleLogManager {
  String filePrefix;
  String fileSuffix;
  
  public FileLogManager(PrintStream stream, TimeSource timeSource, Parameters params) {
    this(stream, timeSource, params, "");  
  }
  
  public FileLogManager(PrintStream stream, TimeSource timeSource, Parameters params, String prefix) {
    this(stream, timeSource, params, prefix,
        params.getString("fileLogManager_filePrefix"),
        params.getString("fileLogManager_fileSuffix"));
  }
  
  public FileLogManager(PrintStream stream, TimeSource timeSource, Parameters params, String prefix, String filePrefix, String fileSuffix) {
    super(stream, timeSource, params, prefix);
    this.filePrefix = filePrefix;
    this.fileSuffix = fileSuffix;
  }
  
  /**
   * Convienience constructor.
   * 
   * Defauts to System.out as the stream, and SimpleTimeSource as the timesource.
   * 
   * @param minPriority the minimum priority to print.
   */  
  public FileLogManager(Parameters params) {
    this(System.out, new SimpleTimeSource(), params);
  }
  
  /**
   * Convienience constructor.
   * 
   * Defauts to SimpleTimeSource as the timesource.
   * 
   * @param stream the stream to write to
   * @param minPriority the minimum priority to print
   */
  public FileLogManager(PrintStream stream, Parameters params) {
    this(stream, new SimpleTimeSource(), params);
  }
  
  /**
   * Convienience constructor.
   * 
   * Defauts to System.out as the stream.
   * 
   * @param timeSource the timesource to get times from
   * @param minPriority the minimum priority to print
   */
  public FileLogManager(TimeSource timeSource, Parameters params) {
    this(System.out, timeSource, params);
  }
  
  public LogManager clone(String detail) {
    try {
      String fname = filePrefix+detail+fileSuffix;
      PrintStream newPS = new PrintStream(new FileOutputStream(fname,true));
      return new FileLogManager(newPS, time, params, "", filePrefix, fileSuffix);
    } catch (IOException ioe) {
      throw new RuntimeException(ioe); 
    }
  }  
}
