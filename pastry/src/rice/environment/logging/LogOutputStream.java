package rice.environment.logging;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

import rice.environment.Environment;

/**
 * This class constructs an output stream that will send its output to a logger, line
 * by line.  This could for example be wrapped in a PrintStream to capture stdout
 * or stderr to the log.  As so:
 * System.setOut(new PrintStream(new LogOutputStream(environment, Logger.INFO, "out"), true));
 * System.setErr(new PrintStream(new LogOutputStream(environment, Logger.INFO, "err"), true));
 * 
 * @author jstewart
 *
 */
public class LogOutputStream extends OutputStream {

  protected Logger logger;
  protected byte[] buffer;
  protected int offset;
  protected int level;
  public static final int BUFFER_SIZE = 1024;
  
  /**
   * Constructs a LogOutputStream
   * 
   * @param env - the environment to log to
   * @param level - the log level of this OutputStream's messages
   */
  public LogOutputStream(Environment env, int level) {
    this(env,level,"");
  }

  /**
   * Constructs a LogOutputStream
   * 
   * @param env - the environment to log to
   * @param level - the log level of this OutputStream's messages
   * @param instance - an instance name string for disambiguation
   */
  public LogOutputStream(Environment env, int level, String instance) {
    logger = env.getLogManager().getLogger(LogOutputStream.class, instance);
    buffer = new byte[BUFFER_SIZE];
    offset = 0;
    this.level = level;
  }

  public void write(int b) throws IOException {
    if (b == '\n') {
      if (buffer[offset] == '\r')
        offset--;
      flush();
      return;
    }
    if (buffer[offset]=='\r') {
      // treat bare \r as a newline
      offset--;
      flush();
    }
    if (offset == buffer.length)
      flush();
    buffer[offset++] = (byte)(b & 0xff);
  }
  
  public void flush() {
    if (offset == 0)
      return;
    if (logger.level <= level) logger.log(new String(buffer, 0, offset));
    offset = 0;
  }
  
  public void close() {
    flush();
  }

}
