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

  // the underlying logger to output to
  protected Logger logger;
  
  // buffer where we build up output
  protected byte[] buffer;
  
  // position where the next write into the buffer would go.
  protected int offset;
  
  // log level to output as
  protected int level;
  
  // size of the output buffer, in bytes
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
      if ((offset > 0) && (buffer[offset-1] == '\r'))
        offset--;
      flush();
      return;
    }
    if ((offset > 0) && buffer[offset-1]=='\r') {
      // treat bare \r as a newline
      offset--;
      flush();
    }
    // I don't actually know why it needs to be - 1 but it works
    if (offset == buffer.length-1)
      flush();
    // okay, so we're not unicode friendly. Cry me a river.
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
