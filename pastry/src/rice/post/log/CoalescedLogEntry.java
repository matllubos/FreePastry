package rice.post.log;

import java.io.*;
import java.security.*;

import rice.*;
import rice.Continuation.*;
import rice.p2p.commonapi.*;
import rice.post.*;
import rice.post.storage.*;
import rice.post.security.*;

/**
 * Class for all entries in the encrypted log.  Applications should *NOT* use
 * or extend this class, as it is internal to the log package.
 * 
 * @version $Id$
 */
final class CoalescedLogEntry extends LogEntry {
  
  // the enclosed log entry
  protected LogEntry[] entries;
  
  /**
   * Constructs a LogEntry
   */
  public CoalescedLogEntry(LogEntry[] entries) {
    this.entries = entries;
  }
  
  /**
   * Returns the enclosed entry
   *
   * @return The enclosed entry
   */
  public LogEntry[] getEntries() {
    return entries;
  }
  
  /**
   * Method which returns the number of current log entries
   *
   * @return The number of yet-to-be-coalesced log entries
   */
  protected int getNumEntries() {
    for (int i=0; i<entries.length; i++) 
      if (entries[i] == null)
        return i;
    
    return entries.length;
  }
  
  /**
   * Method which appends an entry to this coalesed log entry
   *
   * @param entry The entry to append
   */
  protected void appendEntry(LogEntry entry) {
    entries[getNumEntries()] = entry;
    entry.setParent(new PhantomLogEntry(entry));
  }
  
  /**
   * Method which removes an entry which failed on the sync().  SHOULD ONLY
   * BE USED BY COALESED LOG ENTRY!
   *
   * @param entry The entry to append
   */
  protected void removeEntry(LogEntry entry) {
    int i=0;
    
    for (; i<entries.length; i++) 
      if (entries[i] == entry) 
        break;
    
    if (i == entries.length)
      return;
    
    for (; i<entries.length-1; i++) 
      entries[i] = entries[i+1];
    
    entries[i] = null;
  }
  
  /**
   * Method which returns the previous entry for the given entry
   *
   * @param entry The entry to fetch the previous entry for
   * @command The command to return the result to
   */
  protected void getPreviousEntry(LogEntry entry, Continuation command) {
    if (entry == entries[0]) {
      getPreviousEntry(new StandardContinuation(command) {
        public void receiveResult(Object o) {
          if (o instanceof CoalescedLogEntry) {
            LogEntry[] otherEntries = ((CoalescedLogEntry) o).getEntries();
            parent.receiveResult(otherEntries[otherEntries.length-1]);
          } else {
            parent.receiveResult(o);
          }
        }
      });
    } else {
      for (int i=1; i<entries.length; i++) 
        if (entries[i] == entry) {
          command.receiveResult(entries[i-1]);
          return;
        }
      
      command.receiveException(new IllegalArgumentException("ERROR: Could not find previous entry for " + entry));
    } 
  }
  
  /**
   * Called upon deserialization, which tells the log entries who their parent
   * is
   *
   * @param ois The input stream
   */
  private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
    ois.defaultReadObject();
    
    for (int i=0; i<entries.length; i++) 
      if (entries[i] != null)
        entries[i].setParent(new PhantomLogEntry(entries[i]));
  }
  
  /**
   * Internal class for bookkeeping log entries
   */
  protected class PhantomLogEntry extends LogEntry {
    
    // the wrapped entry
    protected LogEntry entry;
    
    /**
     * Constructor which takes the wrapped entry
     */
    public PhantomLogEntry(LogEntry entry) {
      this.entry = entry;
    }
    
    /**
     * Method which redirects the getPreviousEntry back
     * to the Coalesed entry
     *
     * @param command The command to return the result to.
     */
    public void getPreviousEntry(Continuation command) {
      CoalescedLogEntry.this.getPreviousEntry(entry, command);
    }
  }
}

