package rice.post.log;

import java.security.*;

/**
 * Abstract class for all entries in the log. Each application using post should
 * implement a class hierarchy of log entries relevant to the semantics of that
 * system.
 */
public abstract class LogEntry {

  /**
   * Constructs a LogEntry given a pointer to the previous entry
   *
   * @param prev A reference to the previous entry.
   */
  public LogEntry(LogEntryReference prev) {
  }
  
  /**
   * Returns the reference to the previous entry in the log
   *
   * @return A reference to the previous log entry
   */
  public LogEntryReference getPreviousEntry() {
    return null;
  }
}

